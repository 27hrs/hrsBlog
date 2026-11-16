package com.example.hrsblog.repository;

import com.example.hrsblog.domain.FriendLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 友链仓储。
 *
 * <p>所有查询都是 Spring Data 派生方法，参数通过绑定变量传递，
 * 排序与分页由 {@link Pageable} 控制，杜绝字符串拼接 SQL / JPQL。
 */
public interface FriendLinkRepository extends JpaRepository<FriendLink, Long> {

    /** 访客公开列表：仅已通过的友链，按 sort_order 升序、id 升序。 */
    List<FriendLink> findByStatusOrderBySortOrderAscIdAsc(FriendLink.FriendLinkStatus status);

    /** 管理端分页列表（按状态筛选）。 */
    Page<FriendLink> findByStatus(FriendLink.FriendLinkStatus status, Pageable pageable);

    /** 按 IP 统计某日之后的提交次数，用于每日限频。 */
    long countBySubmittedIpAndSubmittedAtGreaterThanEqual(String submittedIp, LocalDateTime since);

    /** 同一站点是否已有待审申请（防止重复提交刷屏）。 */
    boolean existsBySiteUrlAndStatus(String siteUrl, FriendLink.FriendLinkStatus status);
}
