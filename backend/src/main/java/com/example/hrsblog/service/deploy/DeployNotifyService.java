package com.example.hrsblog.service.deploy;

import com.example.hrsblog.config.DeployProperties;
import com.example.hrsblog.domain.SystemLog;
import com.example.hrsblog.event.ContentChangedEvent;
import com.example.hrsblog.repository.SystemLogRepository;
import com.example.hrsblog.service.SiteSettingService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 部署通知：把「内容变更」转换成一次通用的 Webhook 投递。
 *
 * <p>链路：配置实际变更 / 文章发布 → {@link ContentChangedEvent}（事务内发布）
 * → 本类在 {@code AFTER_COMMIT} 阶段接收 → 防抖窗口内合并 → 组装载荷
 * → 交给 {@link WebhookClient} 投递（失败按固定次数重试）→ 每次尝试写一条
 * {@link SystemLog}。
 *
 * <p>设计约束：
 * <ul>
 *   <li><b>不影响主流程</b>：投递失败只记 ERROR 日志，绝不回滚文章 / 配置保存；</li>
 *   <li><b>不占用请求线程</b>：防抖计时与 HTTP 投递都跑在内部专用单线程调度器上；</li>
 *   <li><b>不引入全局异步</b>：不开启 {@code @EnableAsync}，爆炸半径限制在本类内；</li>
 *   <li><b>日志脱敏</b>：{@code Authorization}、Bearer Token 与带查询串的完整地址
 *       一律不落库，只保留主机 + 路径、状态码与截断后的响应片段。</li>
 * </ul>
 */
@Slf4j
@Service
public class DeployNotifyService {

    /** 通用日志中的日志类型。 */
    public static final String LOG_TYPE = "DEPLOY_NOTIFY";

    private static final String SETTING_ENABLED = "webhookEnabled";
    private static final String SETTING_URL = "webhookUrl";
    private static final String SETTING_SECRET = "webhookSecret";
    private static final String SETTING_EVENT_TYPE = "webhookEventType";
    private static final String SETTING_DEBOUNCE_SECONDS = "webhookDebounceSeconds";
    private static final String SETTING_MAX_RETRIES = "webhookMaxRetries";

    /** 详情字段最大长度，避免日志表被超长文本撑爆。 */
    private static final int MAX_DETAIL_LENGTH = 1000;

    private final SiteSettingService siteSettingService;
    private final WebhookClient webhookClient;
    private final SystemLogRepository systemLogRepository;
    private final DeployProperties deployProperties;
    private final ObjectMapper objectMapper;

    /** 专用单线程调度器：防抖计时、重试退避与 HTTP 投递都在这里跑。 */
    private final ScheduledExecutorService executor;

    private final Object monitor = new Object();
    /** 待发送的变更原因（窗口内合并去重）。 */
    private final Set<String> pendingReasons = new LinkedHashSet<>();
    /** 本轮变更的最早发生时间，作为载荷里的 occurredAt。 */
    private Instant pendingSince;
    /** 当前排定的防抖任务。 */
    private ScheduledFuture<?> pendingTask;

    public DeployNotifyService(SiteSettingService siteSettingService,
                               WebhookClient webhookClient,
                               SystemLogRepository systemLogRepository,
                               DeployProperties deployProperties,
                               ObjectMapper objectMapper) {
        this.siteSettingService = siteSettingService;
        this.webhookClient = webhookClient;
        this.systemLogRepository = systemLogRepository;
        this.deployProperties = deployProperties;
        this.objectMapper = objectMapper;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "deploy-notify");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 事务提交后触发：把变更原因并入待发送集合，并重排防抖窗口。
     *
     * <p>这里刻意不读库、不发 HTTP —— 回调运行在「事务刚提交」的请求线程上，
     * 此刻再开事务读库会踩到已提交事务的边界，发 HTTP 则会拖慢接口响应。
     * 两件事都交给专用线程去做。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentChanged(ContentChangedEvent event) {
        if (event == null || event.reasons().isEmpty()) {
            return;
        }
        synchronized (monitor) {
            pendingReasons.addAll(event.reasons());
            if (pendingSince == null || event.occurredAt().isBefore(pendingSince)) {
                pendingSince = event.occurredAt();
            }
        }
        try {
            executor.execute(this::reschedule);
        } catch (RejectedExecutionException ex) {
            log.error("部署通知任务提交失败，本次变更不会触发 Webhook", ex);
        }
    }

    /** 读取最新配置并重排防抖窗口：窗口内的新事件会把延迟重新推后。 */
    private void reschedule() {
        WebhookSettings settings;
        try {
            settings = loadSettings();
        } catch (RuntimeException ex) {
            log.error("读取 Webhook 配置失败，放弃本次部署通知", ex);
            clearPending();
            return;
        }
        if (!settings.enabled() || settings.url().isBlank()) {
            clearPending();
            log.debug("Webhook 未启用或未配置地址，跳过部署通知");
            return;
        }
        long delaySeconds = Math.max(0L, settings.debounceSeconds());
        synchronized (monitor) {
            if (pendingTask != null) {
                pendingTask.cancel(false);
            }
            pendingTask = executor.schedule(this::flush, delaySeconds, TimeUnit.SECONDS);
        }
    }

    /** 防抖窗口到期：取出合并后的原因列表并执行一次投递。 */
    private void flush() {
        List<String> reasons;
        Instant occurredAt;
        synchronized (monitor) {
            reasons = List.copyOf(pendingReasons);
            occurredAt = pendingSince == null ? Instant.now() : pendingSince;
            pendingReasons.clear();
            pendingSince = null;
            pendingTask = null;
        }
        if (reasons.isEmpty()) {
            return;
        }
        WebhookSettings settings;
        try {
            settings = loadSettings();
        } catch (RuntimeException ex) {
            log.error("读取 Webhook 配置失败，放弃本次部署通知", ex);
            return;
        }
        if (!settings.enabled() || settings.url().isBlank()) {
            log.debug("Webhook 已关闭或地址为空，跳过本次部署通知");
            return;
        }
        deliver(settings, reasons, occurredAt);
    }

    /**
     * 投递一次通知：失败按配置的次数重试（指数退避），每次尝试都写一条日志。
     *
     * <p>整个过程不向上抛异常 —— 触发失败既不回滚也不重抛，主流程不受影响。
     */
    private void deliver(WebhookSettings settings, List<String> reasons, Instant occurredAt) {
        String payload;
        try {
            payload = buildPayload(settings.eventType(), reasons, occurredAt);
        } catch (RuntimeException ex) {
            log.error("组装 Webhook 载荷失败", ex);
            writeLog(settings.eventType(), SystemLog.LogStatus.FAILED, 1, "组装 Webhook 载荷失败",
                    sanitizeUrl(settings.url()));
            return;
        }

        int maxAttempts = Math.max(1, settings.maxRetries() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean success;
            String detail;
            try {
                WebhookClient.WebhookResult result = webhookClient.send(settings.url(), settings.secret(), payload);
                success = result.success();
                detail = describeRequest(settings.url(), result.statusCode(), result.responseSummary());
                writeLog(settings.eventType(),
                        success ? SystemLog.LogStatus.SUCCESS : SystemLog.LogStatus.FAILED,
                        attempt,
                        success ? "Webhook 投递成功" : "Webhook 返回失败状态",
                        detail);
            } catch (Exception ex) {
                success = false;
                detail = describeException(settings.url(), ex);
                writeLog(settings.eventType(), SystemLog.LogStatus.FAILED, attempt, "Webhook 投递异常", detail);
            }
            if (success) {
                log.info("部署通知投递成功 eventType={} reasons={} attempt={}", settings.eventType(), reasons, attempt);
                return;
            }
            if (attempt < maxAttempts) {
                long backoff = deployProperties.backoffFor(attempt);
                log.warn("部署通知投递失败，{} 秒后重试 attempt={}/{} detail={}",
                        backoff, attempt, maxAttempts, detail);
                if (!sleep(backoff)) {
                    log.error("部署通知重试被中断，放弃后续尝试");
                    return;
                }
            }
        }
        log.error("部署通知最终失败，已放弃重试 eventType={} reasons={} attempts={}",
                settings.eventType(), reasons, maxAttempts);
    }

    /**
     * 组装载荷：与 GitHub Dispatches API 兼容的 {@code event_type + client_payload} 结构，
     * 对任意通用 Webhook 来说就是一段普通 JSON。
     */
    private String buildPayload(String eventType, List<String> reasons, Instant occurredAt) {
        Map<String, Object> clientPayload = new LinkedHashMap<>();
        clientPayload.put("reasons", reasons);
        clientPayload.put("occurredAt", occurredAt.toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event_type", eventType);
        body.put("client_payload", clientPayload);
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JacksonException ex) {
            throw new IllegalStateException("组装 Webhook 载荷失败", ex);
        }
    }

    /** 读取站点设置里的 Webhook 配置（私密项只在管理端与本服务可见）。 */
    private WebhookSettings loadSettings() {
        Map<String, Object> values = siteSettingService.getAll().values();
        if (values == null) {
            values = Map.of();
        }
        return new WebhookSettings(
                readBoolean(values.get(SETTING_ENABLED)),
                readString(values.get(SETTING_URL)),
                readString(values.get(SETTING_SECRET)),
                readString(values.get(SETTING_EVENT_TYPE)),
                readInt(values.get(SETTING_DEBOUNCE_SECONDS), deployProperties.defaultDebounceSeconds()),
                readInt(values.get(SETTING_MAX_RETRIES), deployProperties.defaultMaxRetries()));
    }

    private void writeLog(String scene, SystemLog.LogStatus status, int attempt, String message, String detail) {
        try {
            SystemLog entry = new SystemLog();
            entry.setType(LOG_TYPE);
            entry.setScene(scene == null || scene.isBlank() ? deployProperties.defaultEventType() : scene);
            entry.setStatus(status);
            entry.setAttempt(attempt);
            entry.setMessage(truncate(message, 500));
            entry.setDetail(truncate(detail, MAX_DETAIL_LENGTH));
            systemLogRepository.save(entry);
        } catch (RuntimeException ex) {
            // 写日志失败绝不能影响投递流程
            log.error("写入系统日志失败 type={} status={}", LOG_TYPE, status, ex);
        }
    }

    /** 清空待发送内容（Webhook 被关闭或配置读取失败时调用）。 */
    private void clearPending() {
        synchronized (monitor) {
            pendingReasons.clear();
            pendingSince = null;
            if (pendingTask != null) {
                pendingTask.cancel(false);
                pendingTask = null;
            }
        }
    }

    /** 退避等待；返回 false 表示线程被中断（应放弃后续重试）。 */
    private boolean sleep(long seconds) {
        if (seconds <= 0) {
            return true;
        }
        try {
            Thread.sleep(TimeUnit.SECONDS.toMillis(seconds));
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 描述一次正常请求的结果：只保留主机路径与状态码，不含查询串与鉴权信息。 */
    private String describeRequest(String url, int statusCode, String responseSummary) {
        StringBuilder detail = new StringBuilder("POST ")
                .append(sanitizeUrl(url))
                .append(" -> HTTP ")
                .append(statusCode);
        if (responseSummary != null && !responseSummary.isBlank()) {
            detail.append(" | ").append(responseSummary);
        }
        return detail.toString();
    }

    /** 描述一次异常：只保留异常类型与消息，不含请求头与请求体。 */
    private String describeException(String url, Exception ex) {
        return "POST " + sanitizeUrl(url) + " -> 异常 "
                + ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    /**
     * 地址脱敏：只保留 scheme + host + port + path，丢弃查询串。
     *
     * <p>查询串可能携带令牌，鉴权头与密钥则根本不会进入日志。
     */
    private String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "(未配置地址)";
        }
        try {
            URI uri = URI.create(url.trim());
            StringBuilder sanitized = new StringBuilder();
            if (uri.getScheme() != null) {
                sanitized.append(uri.getScheme()).append("://");
            }
            if (uri.getHost() != null) {
                sanitized.append(uri.getHost());
            }
            if (uri.getPort() != -1) {
                sanitized.append(':').append(uri.getPort());
            }
            if (uri.getPath() != null) {
                sanitized.append(uri.getPath());
            }
            return sanitized.isEmpty() ? "(非法地址)" : sanitized.toString();
        } catch (IllegalArgumentException ex) {
            return "(非法地址)";
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    private String readString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private int readInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** 关闭调度器，避免应用停机时线程悬挂。 */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /**
     * 生效中的 Webhook 配置快照。
     *
     * @param enabled         是否启用
     * @param url             目标地址
     * @param secret          鉴权密钥（Bearer Token），为空表示不带鉴权头
     * @param eventType       事件类型
     * @param debounceSeconds 防抖窗口（秒）
     * @param maxRetries      失败重试次数（不含首次尝试）
     */
    public record WebhookSettings(
            boolean enabled,
            String url,
            String secret,
            String eventType,
            int debounceSeconds,
            int maxRetries) {
    }
}
