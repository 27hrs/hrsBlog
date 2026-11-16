package com.example.hrsblog.service.deploy;

/**
 * Webhook 投递客户端：把部署通知载荷 POST 到目标地址。
 *
 * <p>抽成接口有两个原因：
 * <ul>
 *   <li>测试可以换成桩实现，不发起真实 HTTP 请求；</li>
 *   <li>将来要支持别的通道（例如发到消息队列）时不必改动防抖与重试逻辑。</li>
 * </ul>
 */
public interface WebhookClient {

    /**
     * 发送一次 Webhook 请求。
     *
     * @param url    目标地址
     * @param secret 鉴权密钥（作为 Bearer Token）；为空表示不带鉴权头
     * @param body   JSON 请求体
     * @return 本次投递结果
     * @throws RuntimeException 网络异常等；调用方负责捕获并按策略重试
     */
    WebhookResult send(String url, String secret, String body);

    /**
     * 一次投递的结果。
     *
     * @param success         是否视为成功（HTTP 2xx）
     * @param statusCode      HTTP 状态码；发生网络异常时为 0
     * @param responseSummary 响应体摘要（已截断，不含敏感信息）
     */
    record WebhookResult(boolean success, int statusCode, String responseSummary) {

        /** 成功结果。 */
        public static WebhookResult success(int statusCode, String responseSummary) {
            return new WebhookResult(true, statusCode, responseSummary);
        }

        /** 失败结果。 */
        public static WebhookResult failure(int statusCode, String responseSummary) {
            return new WebhookResult(false, statusCode, responseSummary);
        }

        /** 按状态码推导结果：2xx 视为成功。 */
        public static WebhookResult of(int statusCode, String responseSummary) {
            return new WebhookResult(statusCode >= 200 && statusCode < 300, statusCode, responseSummary);
        }
    }
}
