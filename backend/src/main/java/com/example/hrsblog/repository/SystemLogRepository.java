package com.example.hrsblog.repository;

import com.example.hrsblog.domain.SystemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 通用系统日志仓储：管理端按 type / status 过滤分页查询，最新在前。
 *
 * <p>排序统一由调用方通过 {@link Pageable} 传入 {@code createdAt DESC, id DESC}，
 * 避免在不同查询方法里重复声明排序规则。
 */
public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {

    /** 按日志类型过滤。 */
    Page<SystemLog> findByType(String type, Pageable pageable);

    /** 按结果状态过滤。 */
    Page<SystemLog> findByStatus(SystemLog.LogStatus status, Pageable pageable);

    /** 同时按类型与状态过滤。 */
    Page<SystemLog> findByTypeAndStatus(String type, SystemLog.LogStatus status, Pageable pageable);
}
