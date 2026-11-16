package com.example.hrsblog.service.deploy;

import com.example.hrsblog.domain.SystemLog;
import com.example.hrsblog.dto.ArticleCreateRequest;
import com.example.hrsblog.dto.ArticleDetailResponse;
import com.example.hrsblog.repository.ArticleRepository;
import com.example.hrsblog.repository.SiteSettingRepository;
import com.example.hrsblog.repository.SystemLogRepository;
import com.example.hrsblog.service.ArticleService;
import com.example.hrsblog.service.SiteSettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 部署通知端到端集成测试：验证「事务提交 → 事件 → Webhook → 系统日志」的真实链路。
 *
 * <p>刻意不加 {@code @Transactional}：只有事务真正提交，{@code AFTER_COMMIT} 监听器才会触发，
 * 而加了事务的用例永远不会提交，也就测不到这条链路。
 *
 * <p>HTTP 调用由 {@link StubWebhookClient} 桩替代，不会发出真实请求。
 */
@SpringBootTest
@ActiveProfiles("test")
class DeployNotifyIntegrationTest {

    /** 替换真实的 RestClient 实现，测试期间不发起任何网络请求。 */
    @TestConfiguration
    static class StubWebhookConfiguration {

        @Bean
        @Primary
        StubWebhookClient stubWebhookClient() {
            return new StubWebhookClient();
        }
    }

    static class StubWebhookClient implements WebhookClient {
        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private volatile CountDownLatch latch = new CountDownLatch(1);

        @Override
        public WebhookResult send(String url, String secret, String body) {
            calls.add(new Call(url, secret, body));
            latch.countDown();
            return WebhookResult.success(204, "");
        }

        void reset(int expectedCalls) {
            calls.clear();
            latch = new CountDownLatch(expectedCalls);
        }

        boolean awaitCalls(int expected, Duration timeout) throws InterruptedException {
            return latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        record Call(String url, String secret, String body) {
        }
    }

    @Autowired
    private SiteSettingService siteSettingService;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private SiteSettingRepository siteSettingRepository;

    @Autowired
    private SystemLogRepository systemLogRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private StubWebhookClient stubWebhookClient;

    @BeforeEach
    void setUp() {
        siteSettingRepository.deleteAllInBatch();
        systemLogRepository.deleteAllInBatch();
        articleRepository.deleteAllInBatch();
        stubWebhookClient.reset(1);
    }

    @AfterEach
    void tearDown() {
        siteSettingRepository.deleteAllInBatch();
        systemLogRepository.deleteAllInBatch();
        articleRepository.deleteAllInBatch();
    }

    @Test
    void enablingWebhookTriggersOneCallAndWritingLog() throws Exception {
        enableWebhook();

        awaitDelivery();
        assertThat(stubWebhookClient.calls).hasSize(1);

        StubWebhookClient.Call call = stubWebhookClient.calls.getFirst();
        assertThat(call.url()).isEqualTo("https://example.test/dispatch");
        assertThat(call.secret()).isEqualTo("test-secret");
        assertThat(call.body()).contains("blog-content-updated").contains("settings_updated");

        awaitTrue(() -> systemLogRepository.count() >= 1, Duration.ofSeconds(10));
        List<SystemLog> logs = systemLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().getStatus()).isEqualTo(SystemLog.LogStatus.SUCCESS);
        assertThat(logs.getFirst().getType()).isEqualTo(DeployNotifyService.LOG_TYPE);
        assertThat(logs.getFirst().getScene()).isEqualTo("blog-content-updated");
        assertThat(logs.getFirst().getAttempt()).isEqualTo(1);
        // 脱敏：详情里只有主机路径与状态码
        assertThat(logs.getFirst().getDetail())
                .contains("https://example.test/dispatch")
                .doesNotContain("test-secret");
    }

    @Test
    void unchangedSettingsDoNotTriggerAnotherCall() throws Exception {
        enableWebhook();
        awaitDelivery();

        stubWebhookClient.reset(1);
        systemLogRepository.deleteAllInBatch();

        // 保存完全相同的配置：不应产生任何投递
        siteSettingService.replaceAll(webhookSettings());

        Thread.sleep(2500);

        assertThat(stubWebhookClient.calls).isEmpty();
        assertThat(systemLogRepository.count()).isZero();
    }

    @Test
    void publishedArticleTriggersWebhookWithArticleReason() throws Exception {
        enableWebhook();
        awaitDelivery();

        stubWebhookClient.reset(1);

        articleService.create(new ArticleCreateRequest(
                "集成测试文章", "integration-notify", "摘要", "# 正文", null, null, "PUBLISHED", Set.of()));

        assertThat(stubWebhookClient.awaitCalls(1, Duration.ofSeconds(10))).isTrue();
        assertThat(stubWebhookClient.calls).hasSize(1);
        assertThat(stubWebhookClient.calls.getFirst().body())
                .contains("article_published")
                .contains("blog-content-updated");
    }

    @Test
    void draftArticleDoesNotTriggerWebhook() throws Exception {
        enableWebhook();
        awaitDelivery();

        stubWebhookClient.reset(1);

        ArticleDetailResponse draft = articleService.create(new ArticleCreateRequest(
                "集成测试草稿", "integration-draft", "摘要", "# 正文", null, null, "DRAFT", Set.of()));

        Thread.sleep(2500);

        assertThat(stubWebhookClient.calls).isEmpty();
        assertThat(draft.status()).isEqualTo("DRAFT");
    }

    // ── 辅助 ──────────────────────────────────────────────

    /**
     * 等待一次投递完整结束：HTTP 调用返回之后，日志行才会落库，
     * 因此两个条件都要等到，断言才不会踩到时序竞争。
     */
    private void awaitDelivery() throws InterruptedException {
        assertThat(stubWebhookClient.awaitCalls(1, Duration.ofSeconds(10))).isTrue();
        awaitTrue(() -> systemLogRepository.count() >= 1, Duration.ofSeconds(10));
    }

    /** 打开 Webhook 开关：这次保存本身就会触发一次投递。 */
    private void enableWebhook() {
        siteSettingService.replaceAll(webhookSettings());
    }

    private Map<String, Object> webhookSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("webhookEnabled", true);
        settings.put("webhookUrl", "https://example.test/dispatch");
        settings.put("webhookSecret", "test-secret");
        settings.put("webhookEventType", "blog-content-updated");
        settings.put("webhookDebounceSeconds", 1);
        settings.put("webhookMaxRetries", 0);
        return settings;
    }

    private void awaitTrue(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
