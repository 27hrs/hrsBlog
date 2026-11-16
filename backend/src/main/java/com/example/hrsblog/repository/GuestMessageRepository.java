package com.example.hrsblog.repository;

import com.example.hrsblog.domain.GuestMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

/**
 * 留言仓储。
 *
 * <p>所有查询都是 Spring Data 派生方法，参数通过绑定变量传递，
 * 排序与分页由 {@link Pageable} 控制，杜绝字符串拼接 SQL / JPQL。
 */
public interface GuestMessageRepository extends JpaRepository<GuestMessage, Long> {

    /**
     * 按状态分页查询：前台公开列表只取 {@code APPROVED}，管理端可按状态筛选。
     *
     * <p>排序由调用方通过 {@link Pageable} 指定（统一为提交时间倒序），不写死在方法名里。
     */
    Page<GuestMessage> findByStatus(GuestMessage.MessageStatus status, Pageable pageable);

    /** 按 IP 统计某日之后的提交次数，用于每日限频。 */
    long countByIpAddressAndCreatedAtGreaterThanEqual(String ipAddress, LocalDateTime since);
}
