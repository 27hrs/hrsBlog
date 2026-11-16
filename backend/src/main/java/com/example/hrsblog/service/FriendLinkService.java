package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.config.FriendLinkProperties;
import com.example.hrsblog.domain.FriendLink;
import com.example.hrsblog.dto.FriendLinkAdminResponse;
import com.example.hrsblog.dto.FriendLinkAdminUpdateRequest;
import com.example.hrsblog.dto.FriendLinkPublicResponse;
import com.example.hrsblog.dto.FriendLinkQuery;
import com.example.hrsblog.dto.FriendLinkReviewRequest;
import com.example.hrsblog.dto.FriendLinkSubmitRequest;
import com.example.hrsblog.event.ContentChangedEvent;
import com.example.hrsblog.repository.FriendLinkRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 友链业务：访客提交申请 → 管理员审核 → 前台展示。
 *
 * <p>可见性规则集中在服务层：访客列表只返回 {@code APPROVED}，并且用
 * {@link FriendLinkPublicResponse} 承载——该 DTO 刻意没有 {@code contact} 字段，
 * 联系方式只在管理端的 {@link FriendLinkAdminResponse} 里出现。
 *
 * <p>防滥用：按提交来源 IP 统计当日提交次数，超过 {@code FriendLinkProperties}
 * 里的限额直接返回 429；同一站点已有待审申请时返回 409，避免重复申请刷屏。
 *
 * <p>发布埋点：审核通过 / 已通过友链被编辑或删除时发布 {@link ContentChangedEvent}，
 * 驱动「内容变更 → Webhook 通知 → CI 重建」链路；待审友链的提交与编辑不触发任何外部通知。
 */
@Slf4j
@Service
public class FriendLinkService {

    private final FriendLinkRepository friendLinkRepository;
    private final FriendLinkProperties friendLinkProperties;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    public FriendLinkService(FriendLinkRepository friendLinkRepository,
                             FriendLinkProperties friendLinkProperties,
                             EntityManager entityManager,
                             ApplicationEventPublisher eventPublisher) {
        this.friendLinkRepository = friendLinkRepository;
        this.friendLinkProperties = friendLinkProperties;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 访客提交友链申请：按 IP 限频 → 查重 → 落库，状态固定为 {@code PENDING}。
     *
     * <p>提交本身不触发任何外部通知：友链要等管理员审核通过后才会出现在前台。
     *
     * @param request  提交内容（已通过 Bean Validation 校验）
     * @param clientIp 提交来源 IP，用于限频统计
     */
    @Transactional
    public FriendLinkPublicResponse submit(FriendLinkSubmitRequest request, String clientIp) {
        enforceDailyLimit(clientIp);

        String siteUrl = request.siteUrl().trim();
        if (friendLinkRepository.existsBySiteUrlAndStatus(siteUrl, FriendLink.FriendLinkStatus.PENDING)) {
            throw new BusinessException(ErrorCode.FRIEND_LINK_DUPLICATE);
        }

        FriendLink friendLink = new FriendLink();
        friendLink.setSiteName(request.siteName().trim());
        friendLink.setSiteUrl(siteUrl);
        friendLink.setLinkPageUrl(request.linkPageUrl().trim());
        friendLink.setAvatarUrl(trimToNull(request.avatarUrl()));
        friendLink.setDescription(trimToNull(request.description()));
        friendLink.setContact(trimToNull(request.contact()));
        friendLink.setStatus(FriendLink.FriendLinkStatus.PENDING);
        friendLink.setSortOrder(0);
        friendLink.setSubmittedIp(clientIp);
        friendLink.setSubmittedAt(LocalDateTime.now());

        FriendLink saved = friendLinkRepository.saveAndFlush(friendLink);
        // created_at / updated_at 由数据库默认值生成，refresh 后回填到响应
        entityManager.refresh(saved);
        // 只记录站点名与来源 IP，联系方式不进日志
        log.info("收到友链申请 id={} siteName={} ip={}", saved.getId(), saved.getSiteName(), clientIp);
        return toPublic(saved);
    }

    /**
     * 访客公开列表：仅已通过的友链，按展示顺序升序。
     */
    @Transactional(readOnly = true)
    public List<FriendLinkPublicResponse> listApproved() {
        return friendLinkRepository.findByStatusOrderBySortOrderAscIdAsc(FriendLink.FriendLinkStatus.APPROVED)
                .stream()
                .map(this::toPublic)
                .toList();
    }

    /**
     * 管理端分页列表：可按状态筛选，默认按展示顺序升序、同序按提交时间倒序。
     */
    @Transactional(readOnly = true)
    public PageResponse<FriendLinkAdminResponse> listForAdmin(FriendLinkQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Order.asc("sortOrder"),
                        Sort.Order.desc("submittedAt"),
                        Sort.Order.desc("id")));
        Page<FriendLink> page = StringUtils.hasText(query.getStatus())
                ? friendLinkRepository.findByStatus(FriendLink.FriendLinkStatus.valueOf(query.getStatus()), pageable)
                : friendLinkRepository.findAll(pageable);
        return PageResponse.of(page, this::toAdmin);
    }

    /**
     * 管理端详情：不存在返回 40403。
     */
    @Transactional(readOnly = true)
    public FriendLinkAdminResponse getForAdmin(Long id) {
        return toAdmin(findOrThrow(id));
    }

    /**
     * 管理端审核：通过或拒绝。
     *
     * <p>只要「审核结果会影响前台展示」就触发重建通知：变为已通过、或原先已通过被改判为拒绝。
     */
    @Transactional
    public FriendLinkAdminResponse review(Long id, FriendLinkReviewRequest request) {
        FriendLink friendLink = findOrThrow(id);
        FriendLink.FriendLinkStatus previousStatus = friendLink.getStatus();
        FriendLink.FriendLinkStatus currentStatus = FriendLink.FriendLinkStatus.valueOf(request.status());

        friendLink.setStatus(currentStatus);
        friendLink.setReviewedAt(LocalDateTime.now());

        FriendLink saved = saveAndRefresh(friendLink);
        log.info("友链审核完成 id={} status={}", saved.getId(), saved.getStatus());
        if (previousStatus == FriendLink.FriendLinkStatus.APPROVED
                || currentStatus == FriendLink.FriendLinkStatus.APPROVED) {
            publishContentChanged();
        }
        return toAdmin(saved);
    }

    /**
     * 管理端编辑：全量字段覆盖；已通过的友链被编辑后需要重建前台。
     */
    @Transactional
    public FriendLinkAdminResponse update(Long id, FriendLinkAdminUpdateRequest request) {
        FriendLink friendLink = findOrThrow(id);
        boolean wasApproved = friendLink.getStatus() == FriendLink.FriendLinkStatus.APPROVED;

        friendLink.setSiteName(request.siteName().trim());
        friendLink.setSiteUrl(request.siteUrl().trim());
        friendLink.setLinkPageUrl(request.linkPageUrl().trim());
        friendLink.setAvatarUrl(trimToNull(request.avatarUrl()));
        friendLink.setDescription(trimToNull(request.description()));
        friendLink.setContact(trimToNull(request.contact()));
        if (request.sortOrder() != null) {
            friendLink.setSortOrder(request.sortOrder());
        }

        FriendLink saved = saveAndRefresh(friendLink);
        log.info("友链已更新 id={} siteName={}", saved.getId(), saved.getSiteName());
        if (wasApproved) {
            publishContentChanged();
        }
        return toAdmin(saved);
    }

    /**
     * 管理端删除：删除已通过的友链同样会改变前台展示，需要重建。
     */
    @Transactional
    public void delete(Long id) {
        FriendLink friendLink = findOrThrow(id);
        boolean wasApproved = friendLink.getStatus() == FriendLink.FriendLinkStatus.APPROVED;
        friendLinkRepository.delete(friendLink);
        log.info("友链已删除 id={}", id);
        if (wasApproved) {
            publishContentChanged();
        }
    }

    /**
     * 按 IP 校验当日提交次数：超过限额直接返回 429。
     *
     * <p>统计口径是「自然日」，日界取服务器本地时区的 00:00（{@link LocalDate#now()}）；
     * 限额为 0 时不限制。重复申请被拒时不会留下记录，因此不占用额度。
     */
    private void enforceDailyLimit(String clientIp) {
        int limit = friendLinkProperties.dailySubmitLimit();
        if (limit <= 0) {
            return;
        }
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        long submittedToday = friendLinkRepository
                .countBySubmittedIpAndSubmittedAtGreaterThanEqual(clientIp, dayStart);
        if (submittedToday >= limit) {
            // 只记 WARN 且不带联系方式，避免日志刷量
            log.warn("友链申请超出当日限额 ip={} limit={} submittedToday={}", clientIp, limit, submittedToday);
            throw new BusinessException(ErrorCode.FRIEND_LINK_RATE_LIMITED);
        }
    }

    private FriendLink findOrThrow(Long id) {
        return friendLinkRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_LINK_NOT_FOUND));
    }

    /** 保存并回填数据库生成的 created_at / updated_at。 */
    private FriendLink saveAndRefresh(FriendLink friendLink) {
        FriendLink saved = friendLinkRepository.saveAndFlush(friendLink);
        entityManager.refresh(saved);
        return saved;
    }

    /** 发布内容变更事件：事务提交后由部署通知服务消费。 */
    private void publishContentChanged() {
        eventPublisher.publishEvent(new ContentChangedEvent(
                List.of(ContentChangedEvent.REASON_FRIEND_LINK_UPDATED), Instant.now()));
    }

    private FriendLinkPublicResponse toPublic(FriendLink friendLink) {
        return new FriendLinkPublicResponse(
                friendLink.getId(),
                friendLink.getSiteName(),
                friendLink.getSiteUrl(),
                friendLink.getLinkPageUrl(),
                friendLink.getAvatarUrl(),
                friendLink.getDescription(),
                friendLink.getSortOrder(),
                friendLink.getCreatedAt());
    }

    private FriendLinkAdminResponse toAdmin(FriendLink friendLink) {
        return new FriendLinkAdminResponse(
                friendLink.getId(),
                friendLink.getSiteName(),
                friendLink.getSiteUrl(),
                friendLink.getLinkPageUrl(),
                friendLink.getAvatarUrl(),
                friendLink.getDescription(),
                friendLink.getContact(),
                friendLink.getStatus().name(),
                friendLink.getSortOrder(),
                friendLink.getSubmittedIp(),
                friendLink.getSubmittedAt(),
                friendLink.getReviewedAt(),
                friendLink.getCreatedAt(),
                friendLink.getUpdatedAt());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
