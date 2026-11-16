package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.common.QqAvatar;
import com.example.hrsblog.config.GuestMessageProperties;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.GuestMessage;
import com.example.hrsblog.domain.MessageReply;
import com.example.hrsblog.dto.GuestMessageAdminResponse;
import com.example.hrsblog.dto.GuestMessagePublicResponse;
import com.example.hrsblog.dto.GuestMessageQuery;
import com.example.hrsblog.dto.GuestMessageReplyRequest;
import com.example.hrsblog.dto.GuestMessageReplyResponse;
import com.example.hrsblog.dto.GuestMessageReviewRequest;
import com.example.hrsblog.dto.GuestMessageSubmitRequest;
import com.example.hrsblog.dto.MessageReplyRow;
import com.example.hrsblog.dto.SettingValuesResponse;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.repository.GuestMessageRepository;
import com.example.hrsblog.repository.MessageReplyRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 留言板业务：访客留言 → 管理员审核 → 前台展示 → 管理员回复。
 *
 * <p>可见性规则集中在服务层：访客列表只返回 {@code APPROVED}，并且用
 * {@link GuestMessagePublicResponse} 承载——该 DTO 刻意没有 {@code email} /
 * {@code ipAddress} / {@code userAgent} 字段，这些只在管理端的
 * {@link GuestMessageAdminResponse} 里出现。
 *
 * <p>防滥用：按提交来源 IP 统计当日提交次数，超过 {@link GuestMessageProperties}
 * 里的限额直接返回 429。
 *
 * <p>与友链模块的关键差异：留言是<b>纯运行时数据</b>，审核、回复、删除都不发布
 * {@link com.example.hrsblog.event.ContentChangedEvent}——留言前台页在运行时直接读接口，
 * 不需要等 CI 重建。
 */
@Slf4j
@Service
public class GuestMessageService {

    /** 前台默认每页条数（站点配置 {@code guestbookPageSize} 缺省时使用）。 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 前台每页条数上限，与站点配置的可选范围保持一致。 */
    public static final int MAX_PAGE_SIZE = 50;

    /** UA 字段在数据库里的列宽，超过就截断。 */
    private static final int MAX_USER_AGENT_LENGTH = 500;

    private final GuestMessageRepository guestMessageRepository;
    private final MessageReplyRepository messageReplyRepository;
    private final AdminUserRepository adminUserRepository;
    private final GuestMessageProperties guestMessageProperties;
    private final SiteSettingService siteSettingService;
    private final EntityManager entityManager;

    public GuestMessageService(GuestMessageRepository guestMessageRepository,
                               MessageReplyRepository messageReplyRepository,
                               AdminUserRepository adminUserRepository,
                               GuestMessageProperties guestMessageProperties,
                               SiteSettingService siteSettingService,
                               EntityManager entityManager) {
        this.guestMessageRepository = guestMessageRepository;
        this.messageReplyRepository = messageReplyRepository;
        this.adminUserRepository = adminUserRepository;
        this.guestMessageProperties = guestMessageProperties;
        this.siteSettingService = siteSettingService;
        this.entityManager = entityManager;
    }

    /**
     * 访客提交留言：按 IP 限频 → 落库，状态固定为 {@code PENDING}。
     *
     * <p>提交本身不触发任何外部通知：留言要等管理员审核通过后才会出现在前台。
     *
     * @param request   提交内容（已通过 Bean Validation 校验）
     * @param clientIp  提交来源 IP，用于限频统计
     * @param userAgent 提交来源 UA，仅用于安全审计，不对外暴露
     */
    @Transactional
    public GuestMessagePublicResponse submit(GuestMessageSubmitRequest request,
                                             String clientIp,
                                             String userAgent) {
        enforceDailyLimit(clientIp);

        GuestMessage message = new GuestMessage();
        message.setNickname(request.nickname().trim());
        message.setEmail(trimToNull(request.email()));
        message.setContent(request.content().trim());
        message.setIpAddress(clientIp);
        message.setUserAgent(truncate(userAgent, MAX_USER_AGENT_LENGTH));
        message.setStatus(GuestMessage.MessageStatus.PENDING);

        GuestMessage saved = guestMessageRepository.saveAndFlush(message);
        // created_at / updated_at 由数据库默认值生成，refresh 后回填到响应
        entityManager.refresh(saved);
        // 只记录 id 与昵称，留言正文与邮箱不进日志
        log.info("收到留言 id={} nickname={} ip={}", saved.getId(), saved.getNickname(), clientIp);
        return toPublic(saved, null);
    }

    /**
     * 访客公开列表：仅已通过的留言，按提交时间倒序分页。
     *
     * <p>回复一次批量取回（{@code findByMessageIdIn}），避免逐条留言再查一次回复的 N+1。
     *
     * @param page       页码，从 0 开始计数
     * @param requestedSize 每页条数；为 {@code null} 时读取站点配置 {@code guestbookPageSize}
     */
    @Transactional(readOnly = true)
    public PageResponse<GuestMessagePublicResponse> listApproved(int page, Integer requestedSize) {
        int size = resolvePageSize(requestedSize);
        Pageable pageable = PageRequest.of(Math.max(page, 0), size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<GuestMessage> result =
                guestMessageRepository.findByStatus(GuestMessage.MessageStatus.APPROVED, pageable);
        Map<Long, GuestMessageReplyResponse> replies = loadReplies(result.getContent());
        return PageResponse.of(result, message -> toPublic(message, replies.get(message.getId())));
    }

    /**
     * 管理端分页列表：可按状态筛选，默认按提交时间倒序。
     */
    @Transactional(readOnly = true)
    public PageResponse<GuestMessageAdminResponse> listForAdmin(GuestMessageQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<GuestMessage> result = StringUtils.hasText(query.getStatus())
                ? guestMessageRepository.findByStatus(
                        GuestMessage.MessageStatus.valueOf(query.getStatus()), pageable)
                : guestMessageRepository.findAll(pageable);
        Map<Long, GuestMessageReplyResponse> replies = loadReplies(result.getContent());
        return PageResponse.of(result, message -> toAdmin(message, replies.get(message.getId())));
    }

    /**
     * 管理端详情：不存在返回 40404。
     */
    @Transactional(readOnly = true)
    public GuestMessageAdminResponse getForAdmin(Long id) {
        GuestMessage message = findOrThrow(id);
        return toAdmin(message, loadReply(message.getId()));
    }

    /**
     * 管理端审核：通过或拒绝。
     *
     * <p>留言是运行时数据，审核不触发任何外部通知（不需要 CI 重建）。
     */
    @Transactional
    public GuestMessageAdminResponse review(Long id, GuestMessageReviewRequest request) {
        GuestMessage message = findOrThrow(id);
        message.setStatus(GuestMessage.MessageStatus.valueOf(request.status()));

        GuestMessage saved = guestMessageRepository.saveAndFlush(message);
        entityManager.refresh(saved);
        log.info("留言审核完成 id={} status={}", saved.getId(), saved.getStatus());
        return toAdmin(saved, loadReply(saved.getId()));
    }

    /**
     * 管理端回复：一条留言至多一条回复，再次提交即覆盖（upsert）。
     *
     * @param id           留言 ID
     * @param request      回复内容
     * @param adminUsername 当前登录的管理员用户名
     */
    @Transactional
    public GuestMessageAdminResponse reply(Long id, GuestMessageReplyRequest request, String adminUsername) {
        GuestMessage message = findOrThrow(id);
        AdminUser admin = adminUserRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        MessageReply reply = messageReplyRepository.findByMessageId(id).orElseGet(MessageReply::new);
        reply.setMessage(message);
        reply.setAdminUser(admin);
        reply.setContent(request.content().trim());

        MessageReply saved = messageReplyRepository.saveAndFlush(reply);
        entityManager.refresh(saved);
        log.info("留言已回复 id={} admin={}", id, adminUsername);
        return toAdmin(message, toReply(saved));
    }

    /**
     * 管理端删除：先删回复再删留言（{@code message_reply.message_id} 外键是 ON DELETE RESTRICT）。
     */
    @Transactional
    public void delete(Long id) {
        GuestMessage message = findOrThrow(id);
        messageReplyRepository.deleteByMessageId(id);
        // 先把回复的删除刷到数据库，再删留言本体，满足外键 RESTRICT 约束
        entityManager.flush();
        guestMessageRepository.delete(message);
        log.info("留言已删除 id={}", id);
    }

    // ── 内部方法 ──────────────────────────────────────────────

    /**
     * 按 IP 校验当日提交次数：超过限额直接返回 429。
     *
     * <p>统计口径是「自然日」，日界取服务器本地时区的 00:00（{@link LocalDate#now()}）；
     * 限额为 0 时不限制。
     */
    private void enforceDailyLimit(String clientIp) {
        int limit = guestMessageProperties.dailySubmitLimit();
        if (limit <= 0) {
            return;
        }
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        long submittedToday = guestMessageRepository
                .countByIpAddressAndCreatedAtGreaterThanEqual(clientIp, dayStart);
        if (submittedToday >= limit) {
            // 只记 WARN 且不带正文与邮箱，避免日志刷量
            log.warn("留言提交超出当日限额 ip={} limit={} submittedToday={}", clientIp, limit, submittedToday);
            throw new BusinessException(ErrorCode.GUEST_MESSAGE_RATE_LIMITED);
        }
    }

    /**
     * 决定每页条数：调用方指定了就用调用方的（仍然夹紧到合法区间），
     * 否则读站点配置 {@code guestbookPageSize}，配置缺失或非法时用默认值。
     */
    private int resolvePageSize(Integer requestedSize) {
        if (requestedSize != null) {
            return Math.min(Math.max(requestedSize, 1), MAX_PAGE_SIZE);
        }
        return configuredPageSize();
    }

    /** 读取站点配置里的「留言每页数量」。 */
    private int configuredPageSize() {
        try {
            SettingValuesResponse publicSettings = siteSettingService.getPublic();
            Object raw = publicSettings == null ? null : publicSettings.values().get("guestbookPageSize");
            if (raw instanceof Number number) {
                int value = number.intValue();
                if (value >= 1) {
                    return Math.min(value, MAX_PAGE_SIZE);
                }
            }
        } catch (RuntimeException ex) {
            // 配置读取失败不应该让留言墙整个挂掉：回退到默认每页条数
            log.debug("读取留言板每页数量失败，回退默认值: {}", ex.getMessage());
        }
        return DEFAULT_PAGE_SIZE;
    }

    /** 批量取回一批留言的回复，返回 messageId → 回复的映射。 */
    private Map<Long, GuestMessageReplyResponse> loadReplies(List<GuestMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        List<Long> messageIds = messages.stream().map(GuestMessage::getId).toList();
        Map<Long, GuestMessageReplyResponse> replies = new LinkedHashMap<>();
        for (MessageReplyRow row : messageReplyRepository.findByMessageIdIn(messageIds)) {
            replies.put(row.messageId(), new GuestMessageReplyResponse(
                    row.id(), row.content(), row.createdAt()));
        }
        return replies;
    }

    /** 取单条留言的回复（不存在返回 {@code null}）。 */
    private GuestMessageReplyResponse loadReply(Long messageId) {
        return messageReplyRepository.findByMessageId(messageId)
                .map(this::toReply)
                .orElse(null);
    }

    private GuestMessage findOrThrow(Long id) {
        return guestMessageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUEST_MESSAGE_NOT_FOUND));
    }

    private GuestMessagePublicResponse toPublic(GuestMessage message, GuestMessageReplyResponse reply) {
        return new GuestMessagePublicResponse(
                message.getId(),
                message.getNickname(),
                QqAvatar.derive(message.getEmail()),
                message.getContent(),
                message.getCreatedAt(),
                reply == null ? List.of() : List.of(reply));
    }

    private GuestMessageAdminResponse toAdmin(GuestMessage message, GuestMessageReplyResponse reply) {
        return new GuestMessageAdminResponse(
                message.getId(),
                message.getNickname(),
                message.getEmail(),
                message.getContent(),
                message.getIpAddress(),
                message.getUserAgent(),
                message.getStatus().name(),
                message.getCreatedAt(),
                message.getUpdatedAt(),
                reply);
    }

    private GuestMessageReplyResponse toReply(MessageReply reply) {
        return new GuestMessageReplyResponse(reply.getId(), reply.getContent(), reply.getCreatedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
