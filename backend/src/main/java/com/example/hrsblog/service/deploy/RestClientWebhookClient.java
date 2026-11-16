package com.example.hrsblog.service.deploy;

import com.example.hrsblog.config.DeployProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;

/**
 * 基于 Spring 内置 {@link RestClient} 的 Webhook 客户端实现，不引入任何新依赖。
 *
 * <p>请求形态与 GitHub Dispatches API 一致：
 * <pre>
 * POST {webhookUrl}
 * Authorization: Bearer {webhookSecret}   # secret 为空时完全不带这个头
 * Content-Type: application/json
 *
 * {"event_type":"...","client_payload":{...}}
 * </pre>
 * 对通用 Webhook 来说这就是一段普通 JSON。
 */
@Slf4j
@Component
public class RestClientWebhookClient implements WebhookClient {

    /** 响应摘要最大长度，避免把整页 HTML 塞进日志表。 */
    private static final int MAX_SUMMARY_LENGTH = 300;

    private final RestClient restClient;

    public RestClientWebhookClient(DeployProperties deployProperties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(deployProperties.connectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(deployProperties.readTimeoutSeconds()));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public WebhookResult send(String url, String secret, String body) {
        try {
            ResponseEntity<String> response = restClient.post()
                    // 用 URI 而不是字符串模板：地址里出现 {} 时不会被当成占位符展开
                    .uri(URI.create(url.trim()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (secret != null && !secret.isBlank()) {
                            headers.setBearerAuth(secret.trim());
                        }
                    })
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            return WebhookResult.of(response.getStatusCode().value(), summarize(response.getBody()));
        } catch (RestClientResponseException ex) {
            // 4xx / 5xx 不往上抛：交给上层记一条 FAILED 日志并按策略重试
            return WebhookResult.failure(ex.getStatusCode().value(), summarize(ex.getResponseBodyAsString()));
        }
    }

    /** 把响应压成单行摘要（截断），避免把整页 HTML 写进日志。 */
    private String summarize(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        String flattened = responseBody.replaceAll("\\s+", " ").trim();
        return flattened.length() <= MAX_SUMMARY_LENGTH
                ? flattened
                : flattened.substring(0, MAX_SUMMARY_LENGTH) + "…";
    }
}
