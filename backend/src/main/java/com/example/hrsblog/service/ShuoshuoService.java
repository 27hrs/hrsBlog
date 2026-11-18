package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.domain.Shuoshuo;
import com.example.hrsblog.dto.ShuoshuoAdminResponse;
import com.example.hrsblog.dto.ShuoshuoCreateRequest;
import com.example.hrsblog.dto.ShuoshuoPublicResponse;
import com.example.hrsblog.dto.ShuoshuoQuery;
import com.example.hrsblog.dto.ShuoshuoUpdateRequest;
import com.example.hrsblog.event.ContentChangedEvent;
import com.example.hrsblog.repository.ShuoshuoRepository;
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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 说说业务：管理员撰写草稿 → 发布 → 访客端按时间线阅读。
 *
 * <p>可见性规则集中在服务层：访客列表只查询 {@code visibility = PUBLIC}，
 * 草稿对外完全不可见（管理端接口不返回草稿以外的东西，但访客端一律看不到）。
 *
 * <p>时间线语义：
 * <ul>
 *   <li>发布时若尚未填写发布时间，回填当前时间；</li>
 *   <li>撤回（{@code unpublish}）只改可见性，<b>保留</b>原发布时间，
 *       这样重新发布时条目回到原来的时间线位置，而不是跳到最新一条。</li>
 * </ul>
 *
 * <p>发布埋点：发布 / 撤回 / 删除 / 公开内容的编辑时发布 {@link ContentChangedEvent}，
 * 驱动「内容变更 → Webhook 通知 → CI 重建」链路；
 * 草稿（PRIVATE）的创建与编辑不触发任何外部通知。
 */
@Slf4j
@Service
public class ShuoshuoService {

    private final ShuoshuoRepository shuoshuoRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    public ShuoshuoService(ShuoshuoRepository shuoshuoRepository,
                            EntityManager entityManager,
                            ApplicationEventPublisher eventPublisher) {
        this.shuoshuoRepository = shuoshuoRepository;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 新建说说：一律落成私有草稿，发布时间留空。
     *
     * <p>草稿的创建不触发任何外部通知——它还没有出现在访客端。
     *
     * @param request 说说正文（已通过 Bean Validation 校验）
     * @return 管理端视图（含可见性与时间戳）
     */
    @Transactional
    public ShuoshuoAdminResponse create(ShuoshuoCreateRequest request) {
        Shuoshuo shuoshuo = new Shuoshuo();
        shuoshuo.setContent(request.content().trim());
        shuoshuo.setVisibility(Shuoshuo.Visibility.PRIVATE);

        Shuoshuo saved = saveAndRefresh(shuoshuo);
        log.info("说说已创建 id={} visibility={}", saved.getId(), saved.getVisibility());
        return toAdmin(saved);
    }

    /**
     * 更新说说正文：只改内容，不动可见性与发布时间。
     *
     * <p>原先就是公开（PUBLIC）的条目被编辑时，前台内容发生了变化，需要触发重建通知；
     * 草稿的编辑不触发任何外部通知。
     *
     * @param id      说说主键
     * @param request 新的正文（已通过 Bean Validation 校验）
     * @return 更新后的管理端视图
     */
    @Transactional
    public ShuoshuoAdminResponse update(Long id, ShuoshuoUpdateRequest request) {
        Shuoshuo shuoshuo = findOrThrow(id);
        // 变更前的可见性：用来区分「公开内容被编辑」与「草稿被编辑」
        boolean wasPublic = shuoshuo.getVisibility() == Shuoshuo.Visibility.PUBLIC;

        shuoshuo.setContent(request.content().trim());

        Shuoshuo saved = saveAndRefresh(shuoshuo);
        log.info("说说已更新 id={} visibility={}", saved.getId(), saved.getVisibility());
        if (wasPublic) {
            publishContentChanged();
        }
        return toAdmin(saved);
    }

    /**
     * 发布说说：置为公开；发布时间为空时回填当前时间。
     *
     * <p>已发布过的条目再次发布时保留原发布时间，回到原来的时间线位置。
     *
     * @param id 说说主键
     * @return 发布后的管理端视图
     */
    @Transactional
    public ShuoshuoAdminResponse publish(Long id) {
        Shuoshuo shuoshuo = findOrThrow(id);

        shuoshuo.setVisibility(Shuoshuo.Visibility.PUBLIC);
        // 首次发布回填发布时间；重复发布保留原发布时间
        if (shuoshuo.getPublishedAt() == null) {
            shuoshuo.setPublishedAt(LocalDateTime.now());
        }

        Shuoshuo saved = saveAndRefresh(shuoshuo);
        log.info("说说已发布 id={} publishedAt={}", saved.getId(), saved.getPublishedAt());
        publishContentChanged();
        return toAdmin(saved);
    }

    /**
     * 撤回说说：置为私有，但<b>保留</b>原发布时间。
     *
     * <p>保留发布时间是为了让「撤回 → 重新发布」回到原来的时间线位置，
     * 而不是突然跳到最新一条。
     *
     * @param id 说说主键
     * @return 撤回后的管理端视图
     */
    @Transactional
    public ShuoshuoAdminResponse unpublish(Long id) {
        Shuoshuo shuoshuo = findOrThrow(id);

        shuoshuo.setVisibility(Shuoshuo.Visibility.PRIVATE);
        // publishedAt 刻意不改：重新发布时回到原时间线位置

        Shuoshuo saved = saveAndRefresh(shuoshuo);
        log.info("说说已撤回 id={} publishedAt={}", saved.getId(), saved.getPublishedAt());
        publishContentChanged();
        return toAdmin(saved);
    }

    /**
     * 删除说说：硬删除（表中没有软删字段）。
     *
     * @param id 说说主键
     */
    @Transactional
    public void delete(Long id) {
        Shuoshuo shuoshuo = findOrThrow(id);
        shuoshuoRepository.delete(shuoshuo);
        log.info("说说已删除 id={}", id);
        publishContentChanged();
    }

    /**
     * 管理端分页列表：草稿与已发布均可见，按创建时间倒序，可选按可见性筛选。
     *
     * @param query 查询参数（分页 + 可选 visibility）
     * @return 管理端分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<ShuoshuoAdminResponse> listForAdmin(ShuoshuoQuery query) {
        Page<Shuoshuo> page;
        if (StringUtils.hasText(query.getVisibility())) {
            // 筛选时排序同样由 Pageable 指定，与不筛选时保持一致
            Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                    Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
            page = shuoshuoRepository.findByVisibility(
                    Shuoshuo.Visibility.valueOf(query.getVisibility()), pageable);
        } else {
            // 不筛选时用仓储自带的排序方法（created_at 倒序、id 倒序）
            page = shuoshuoRepository.findAllByOrderByCreatedAtDescIdDesc(
                    PageRequest.of(query.getPage(), query.getSize()));
        }
        return PageResponse.of(page, this::toAdmin);
    }

    /**
     * 访客端分页列表：仅公开条目，按发布时间倒序、id 倒序。
     *
     * @param query 查询参数（分页；visibility 对访客无效，恒为 PUBLIC）
     * @return 访客端分页结果
     */
    @Transactional(readOnly = true)
    public PageResponse<ShuoshuoPublicResponse> listPublic(ShuoshuoQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
        Page<Shuoshuo> page = shuoshuoRepository.findByVisibility(Shuoshuo.Visibility.PUBLIC, pageable);
        return PageResponse.of(page, this::toPublic);
    }

    /**
     * 管理端详情：草稿与已发布均可见，不存在返回 {@link ErrorCode#NOT_FOUND}。
     *
     * @param id 说说主键
     * @return 管理端视图
     */
    @Transactional(readOnly = true)
    public ShuoshuoAdminResponse getForAdmin(Long id) {
        return toAdmin(findOrThrow(id));
    }

    /** 按主键取说说，找不到时抛 404。 */
    private Shuoshuo findOrThrow(Long id) {
        return shuoshuoRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    /** 保存并回填数据库生成的 created_at / updated_at。 */
    private Shuoshuo saveAndRefresh(Shuoshuo shuoshuo) {
        Shuoshuo saved = shuoshuoRepository.saveAndFlush(shuoshuo);
        entityManager.refresh(saved);
        return saved;
    }

    /** 发布内容变更事件：事务提交后由部署通知服务消费。 */
    private void publishContentChanged() {
        eventPublisher.publishEvent(new ContentChangedEvent(
                List.of(ContentChangedEvent.REASON_SHUOSHUO_UPDATED), Instant.now()));
    }

    private ShuoshuoAdminResponse toAdmin(Shuoshuo shuoshuo) {
        return new ShuoshuoAdminResponse(
                shuoshuo.getId(),
                shuoshuo.getContent(),
                shuoshuo.getVisibility().name(),
                shuoshuo.getPublishedAt(),
                shuoshuo.getCreatedAt(),
                shuoshuo.getUpdatedAt());
    }

    private ShuoshuoPublicResponse toPublic(Shuoshuo shuoshuo) {
        return new ShuoshuoPublicResponse(
                shuoshuo.getId(),
                shuoshuo.getContent(),
                shuoshuo.getPublishedAt());
    }
}
