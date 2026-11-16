package com.example.hrsblog.service;

import com.example.hrsblog.config.DeployProperties;
import com.example.hrsblog.domain.SystemLog;
import com.example.hrsblog.dto.SettingValuesResponse;
import com.example.hrsblog.event.ContentChangedEvent;
import com.example.hrsblog.repository.SystemLogRepository;
import com.example.hrsblog.service.deploy.DeployNotifyService;
import com.example.hrsblog.service.deploy.WebhookClient;
import com.example.hrsblog.service.deploy.WebhookClient.WebhookResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 部署通知单元测试：防抖合并、固定次数重试、每次尝试落日志、失败不影响主流程。
 *
 * <p>{@link WebhookClient} 用桩实现替换，不会发起真实 HTTP 请求；
 * 防抖窗口与退避时长都压到最短，保证用例在秒级内完成。
 */
@ExtendWith(MockitoExtension.class)
class DeployNotifyServiceTest {

    @Mock
    private SiteSettingService siteSettingService;

    @Mock
    private SystemLogRepository systemLogRepository;

    private DeployProperties deployProperties;
    private DeployNotifyService deployNotifyService;
    private RecordingWebhookClient recorder;

    /** 记录每次投递调用，便于断言请求体与调用次数。 */
    private static final class RecordingWebhookClient implements WebhookClient {
        private final List<Call> calls = new CopyOnWriteArrayList<>();
        private final CountDownLatch latch;
        private final List<WebhookResult> scriptedResults;
        private RuntimeException failure;

        RecordingWebhookClient(int expectedCalls, List<WebhookResult> scriptedResults) {
            this.latch = new CountDownLatch(expectedCalls);
            this.scriptedResults = scriptedResults;
        }

        @Override
        public WebhookResult send(String url, String secret, String body) {
            int index = calls.size();
            calls.add(new Call(url, secret, body));
            latch.countDown();
            if (failure != null) {
                throw failure;
            }
            if (scriptedResults.isEmpty()) {
                return WebhookResult.success(204, "");
            }
            return scriptedResults.get(Math.min(index, scriptedResults.size() - 1));
        }

        record Call(String url, String secret, String body) {
        }
    }

    @BeforeEach
    void setUp() {
        // 退避为 0：重试之间不真正等待
        deployProperties = new DeployProperties("blog-content-updated", 10, 2, List.of(0L, 0L), 2, 3);
        lenient().when(systemLogRepository.save(any(SystemLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        if (deployNotifyService != null) {
            deployNotifyService.shutdown();
        }
    }

    // ── 防抖合并 ──────────────────────────────────────────

    @Test
    void eventsInsideDebounceWindowAreMergedIntoASingleCall() throws Exception {
        recorder = new RecordingWebhookClient(1, List.of());
        when(siteSettingService.getAll())
                .thenReturn(settings(true, "https://example.test/dispatch", "secret", 1, 2));
        deployNotifyService = newService();

        deployNotifyService.onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_SETTINGS_UPDATED));
        deployNotifyService.onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_ARTICLE_PUBLISHED));
        deployNotifyService.onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_ARTICLE_UPDATED));

        assertThat(recorder.latch.await(10, TimeUnit.SECONDS)).isTrue();
        // 防抖窗口结束后再等一会儿，确认没有第二次调用
        Thread.sleep(300);

        assertThat(recorder.calls).hasSize(1);
        RecordingWebhookClient.Call call = recorder.calls.getFirst();
        assertThat(call.url()).isEqualTo("https://example.test/dispatch");
        assertThat(call.secret()).isEqualTo("secret");
        assertThat(call.body())
                .contains("blog-content-updated")
                .contains("settings_updated")
                .contains("article_published")
                .contains("article_updated")
                .contains("occurredAt");
        // 载荷只带原因与时间戳，不带任何正文或密钥
        assertThat(call.body()).doesNotContain("secret").doesNotContain("\"title\"");
    }

    @Test
    void disabledWebhookDoesNotCallOut() throws Exception {
        recorder = new RecordingWebhookClient(1, List.of());
        when(siteSettingService.getAll())
                .thenReturn(settings(false, "https://example.test/dispatch", "", 1, 2));
        deployNotifyService = newService();

        deployNotifyService.onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_ARTICLE_PUBLISHED));

        assertThat(recorder.latch.await(1, TimeUnit.SECONDS)).isFalse();
        assertThat(recorder.calls).isEmpty();
    }

    @Test
    void blankWebhookUrlDoesNotCallOut() throws Exception {
        recorder = new RecordingWebhookClient(1, List.of());
        when(siteSettingService.getAll())
                .thenReturn(settings(true, "   ", "", 1, 2));
        deployNotifyService = newService();

        deployNotifyService.onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_ARTICLE_PUBLISHED));

        assertThat(recorder.latch.await(1, TimeUnit.SECONDS)).isFalse();
        assertThat(recorder.calls).isEmpty();
    }

    @Test
    void emptyReasonEventIsIgnored() {
        recorder = new RecordingWebhookClient(1, List.of());
        deployNotifyService = newService();

        deployNotifyService.onContentChanged(new ContentChangedEvent(List.of(), Instant.now()));

        verify(siteSettingService, never()).getAll();
        assertThat(recorder.calls).isEmpty();
    }

    // ── 重试与日志 ──────────────────────────────────────────

    @Test
    void failedDeliveryIsRetriedAndEveryAttemptIsLogged() {
        recorder = new RecordingWebhookClient(3, List.of(WebhookResult.failure(500, "server error")));
        when(siteSettingService.getAll())
                .thenReturn(settings(true, "https://example.test/dispatch", "secret", 0, 2));
        deployNotifyService = newService();

        deployNotifyService.onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_ARTICLE_PUBLISHED));

        awaitTrue(() -> recorder.calls.size() >= 3, Duration.ofSeconds(10));

        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository, times(3)).save(captor.capture());

        List<SystemLog> logs = captor.getAllValues();
        assertThat(logs).allMatch(entry -> entry.getStatus() == SystemLog.LogStatus.FAILED);
        assertThat(logs).extracting(SystemLog::getAttempt).containsExactly(1, 2, 3);
        assertThat(logs).allMatch(entry -> DeployNotifyService.LOG_TYPE.equals(entry.getType()));
        assertThat(logs).allMatch(entry -> "blog-content-updated".equals(entry.getScene()));
        // 脱敏：日志里只保留主机路径与状态码，不含查询串与密钥
        assertThat(logs.getFirst().getDetail())
                .contains("https://example.test/dispatch")
                .contains("500")
                .doesNotContain("secret");
    }

    @Test
    void successAfterRetryIsLoggedAsSuccess() {
        recorder = new RecordingWebhookClient(2,
                List.of(WebhookResult.failure(502, "bad gateway"), WebhookResult.success(204, "")));
        when(siteSettingService.getAll())
                .thenReturn(settings(true, "https://example.test/dispatch", "secret", 0, 2));
        deployNotifyService = newService();

        deployNotifyService.onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_ARTICLE_PUBLISHED));

        awaitTrue(() -> recorder.calls.size() >= 2, Duration.ofSeconds(10));
        awaitTrue(() -> capturedLogs().size() >= 2, Duration.ofSeconds(10));

        List<SystemLog> logs = capturedLogs();
        assertThat(logs.getFirst().getStatus()).isEqualTo(SystemLog.LogStatus.FAILED);
        assertThat(logs.getFirst().getAttempt()).isEqualTo(1);
        assertThat(logs.get(1).getStatus()).isEqualTo(SystemLog.LogStatus.SUCCESS);
        assertThat(logs.get(1).getAttempt()).isEqualTo(2);
    }

    @Test
    void deliveryExceptionIsSwallowedAndLoggedAsFailed() {
        recorder = new RecordingWebhookClient(1, List.of());
        recorder.failure = new IllegalStateException("connection refused");
        when(siteSettingService.getAll())
                .thenReturn(settings(true, "https://example.test/dispatch", "secret", 0, 0));
        deployNotifyService = newService();

        assertThatCode(() -> deployNotifyService
                .onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_ARTICLE_PUBLISHED)))
                .doesNotThrowAnyException();

        awaitTrue(() -> capturedLogs().size() >= 1, Duration.ofSeconds(10));

        List<SystemLog> logs = capturedLogs();
        assertThat(logs.getFirst().getStatus()).isEqualTo(SystemLog.LogStatus.FAILED);
        assertThat(logs.getFirst().getMessage()).contains("Webhook 投递异常");
        assertThat(logs.getFirst().getDetail()).contains("IllegalStateException");
    }

    @Test
    void zeroRetryConfigurationStopsAfterFirstAttempt() throws InterruptedException {
        recorder = new RecordingWebhookClient(1, List.of(WebhookResult.failure(500, "boom")));
        when(siteSettingService.getAll())
                .thenReturn(settings(true, "https://example.test/dispatch", "secret", 0, 0));
        deployNotifyService = newService();

        deployNotifyService.onContentChanged(ContentChangedEvent.of(ContentChangedEvent.REASON_ARTICLE_PUBLISHED));

        awaitTrue(() -> recorder.calls.size() >= 1, Duration.ofSeconds(10));
        Thread.sleep(200);

        assertThat(recorder.calls).hasSize(1);
        verify(systemLogRepository, times(1)).save(any(SystemLog.class));
    }

    // ── 辅助 ──────────────────────────────────────────────

    private DeployNotifyService newService() {
        return new DeployNotifyService(siteSettingService, recorder, systemLogRepository,
                deployProperties, new ObjectMapper());
    }

    private List<SystemLog> capturedLogs() {
        ArgumentCaptor<SystemLog> captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(systemLogRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private SettingValuesResponse settings(boolean enabled, String url, String secret,
                                          int debounceSeconds, int maxRetries) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("webhookEnabled", enabled);
        values.put("webhookUrl", url);
        values.put("webhookSecret", secret);
        values.put("webhookEventType", "blog-content-updated");
        values.put("webhookDebounceSeconds", debounceSeconds);
        values.put("webhookMaxRetries", maxRetries);
        return new SettingValuesResponse(values);
    }

    private void awaitTrue(BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
