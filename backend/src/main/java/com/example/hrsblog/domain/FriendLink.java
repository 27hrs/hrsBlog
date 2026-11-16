package com.example.hrsblog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 友链实体：访客在前台提交的友链申请，管理端审核通过后才会出现在博客前台。
 *
 * <p>{@code contact}（联系方式）只允许出现在管理端响应里：访客端的
 * {@code FriendLinkPublicResponse} 刻意不包含该字段，避免被爬虫批量采集。
 *
 * <p>{@code submittedIp} 记录提交来源 IP，配合 {@code FriendLinkProperties} 里的
 * 每日限额做基础防滥用；{@code submittedAt} 既是提交时间，也是限频统计的时间依据。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "friend_link")
public class FriendLink {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String siteName;

    @Column(nullable = false, length = 500)
    private String siteUrl;

    @Column(name = "link_page_url", nullable = false, length = 500)
    private String linkPageUrl;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(length = 500)
    private String description;

    @Column(length = 200)
    private String contact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FriendLinkStatus status = FriendLinkStatus.PENDING;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "submitted_ip", length = 45)
    private String submittedIp;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    private LocalDateTime updatedAt;

    public enum FriendLinkStatus { PENDING, APPROVED, REJECTED }
}
