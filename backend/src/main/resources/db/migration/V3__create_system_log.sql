-- HrsBlog V3：通用系统日志表。
-- 记录「内容变更 → Webhook 通知 → CI 重建」链路的每一次投递尝试，便于管理端回溯。
-- 字段与 domain/SystemLog.java 严格一一对应，满足 spring.jpa.hibernate.ddl-auto=validate。
-- 注意：列名用 log_type 而不是 type —— type 是 MySQL 保留字。

CREATE TABLE system_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志唯一标识',
    log_type VARCHAR(32) NOT NULL COMMENT '日志类型，例如 DEPLOY_NOTIFY',
    scene VARCHAR(64) NOT NULL COMMENT '业务场景，例如 blog-content-updated',
    status VARCHAR(16) NOT NULL COMMENT '本次尝试的结果：SUCCESS 成功，FAILED 失败',
    attempt INT NOT NULL DEFAULT 1 COMMENT '第几次尝试，从 1 开始',
    message VARCHAR(500) NOT NULL COMMENT '结果摘要，不含任何密钥',
    detail TEXT NULL COMMENT '脱敏详情：只保留主机路径、状态码与响应片段',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '日志写入时间',
    PRIMARY KEY (id),
    KEY idx_system_log_type_created (log_type, created_at),
    KEY idx_system_log_status_created (status, created_at),
    KEY idx_system_log_created (created_at),
    CONSTRAINT ck_system_log_status CHECK (status IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='通用系统日志表';
