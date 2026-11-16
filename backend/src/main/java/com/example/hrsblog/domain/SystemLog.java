package com.example.hrsblog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 通用系统日志：记录自动化链路（如「内容变更 → Webhook → CI 重建」）的每一次尝试结果。
 *
 * <p>与业务表（article / site_setting）不同，这里记录的是「系统的动作」而不是「用户的数据」：
 * 一条日志对应一次投递尝试，因此 {@code attempt} 从 1 开始，重试会各写一条。
 *
 * <p>脱敏约定：{@code detail} 只允许出现目标主机 + 路径、HTTP 方法与状态码、截断后的响应片段。
 * 鉴权头、Bearer Token 与带查询串的完整 URL 一律不写入。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "system_log")
public class SystemLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 日志类型，例如 {@code DEPLOY_NOTIFY}。 */
    @Column(name = "log_type", nullable = false, length = 32)
    private String type;

    /** 业务场景，例如 {@code blog-content-updated}。 */
    @Column(name = "scene", nullable = false, length = 64)
    private String scene;

    /** 本次尝试的结果。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private LogStatus status = LogStatus.SUCCESS;

    /** 第几次尝试，从 1 开始。 */
    @Column(name = "attempt", nullable = false)
    private int attempt = 1;

    /** 结果摘要。 */
    @Column(name = "message", nullable = false, length = 500)
    private String message;

    /** 脱敏后的详情。 */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    private LocalDateTime createdAt;

    /**
     * 写入前补齐时间戳。
     *
     * <p>列上同时保留了数据库默认值 {@code CURRENT_TIMESTAMP(3)}（供直接 SQL 写入兜底），
     * 但由应用侧赋值，读取接口才能立刻拿到 {@code createdAt}，不必再回表刷新实体。
     */
    @PrePersist
    void onPersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** 单次尝试的结果状态。 */
    public enum LogStatus {
        /** 投递成功。 */
        SUCCESS,
        /** 投递失败（网络异常或非 2xx 响应）。 */
        FAILED
    }
}
