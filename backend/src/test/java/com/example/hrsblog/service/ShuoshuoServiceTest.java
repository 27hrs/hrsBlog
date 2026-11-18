package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.Shuoshuo;
import com.example.hrsblog.dto.ShuoshuoAdminResponse;
import com.example.hrsblog.dto.ShuoshuoCreateRequest;
import com.example.hrsblog.dto.ShuoshuoQuery;
import com.example.hrsblog.dto.ShuoshuoUpdateRequest;
import com.example.hrsblog.event.ContentChangedEvent;
import com.example.hrsblog.repository.ShuoshuoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 说说服务单元测试：发布时间语义（发布回填 / 撤回保留）与埋点规则。
 *
 * <p>埋点规则（与产品约定一致）：
 * <ul>
 *   <li>新建草稿 → 不触发；</li>
 *   <li>草稿的编辑 → 不触发；</li>
 *   <li>公开说说的编辑 → 触发 {@code shuoshuo_updated}；</li>
 *   <li>发布 / 撤回 / 删除 → 触发 {@code shuoshuo_updated}。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ShuoshuoServiceTest {

    @Mock
    private ShuoshuoRepository shuoshuoRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ShuoshuoService shuoshuoService;

    @BeforeEach
    void setUp() {
        shuoshuoService = new ShuoshuoService(shuoshuoRepository, entityManager, eventPublisher);
    }

    // ── 创建 ──────────────────────────────────────────────

    @Test
    void creatingShuoshuoStoresPrivateDraftWithoutEvent() {
        when(shuoshuoRepository.saveAndFlush(any(Shuoshuo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShuoshuoAdminResponse response = shuoshuoService.create(new ShuoshuoCreateRequest("  今天写点东西  "));

        ArgumentCaptor<Shuoshuo> captor = ArgumentCaptor.forClass(Shuoshuo.class);
        verify(shuoshuoRepository).saveAndFlush(captor.capture());
        Shuoshuo saved = captor.getValue();
        assertThat(saved.getVisibility()).isEqualTo(Shuoshuo.Visibility.PRIVATE);
        assertThat(saved.getPublishedAt()).isNull();
        assertThat(response.visibility()).isEqualTo("PRIVATE");
        assertThat(response.content()).isEqualTo("今天写点东西");
        assertThat(response.publishedAt()).isNull();
        // 草稿的创建不触发任何外部通知
        verifyNoInteractions(eventPublisher);
    }

    // ── 发布 / 撤回 ───────────────────────────────────────

    @Test
    void publishingShuoshuoBackfillsPublishedAtAndPublishesEvent() {
        Shuoshuo draft = shuoshuo(Shuoshuo.Visibility.PRIVATE, null);
        when(shuoshuoRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(shuoshuoRepository.saveAndFlush(any(Shuoshuo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShuoshuoAdminResponse response = shuoshuoService.publish(1L);

        assertThat(response.visibility()).isEqualTo("PUBLIC");
        assertThat(response.publishedAt()).isNotNull();
        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    @Test
    void publishingAgainKeepsOriginalPublishedAt() {
        LocalDateTime originalPublishedAt = LocalDateTime.now().minusDays(3);
        Shuoshuo draft = shuoshuo(Shuoshuo.Visibility.PRIVATE, originalPublishedAt);
        when(shuoshuoRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(shuoshuoRepository.saveAndFlush(any(Shuoshuo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShuoshuoAdminResponse response = shuoshuoService.publish(1L);

        assertThat(response.publishedAt()).isEqualTo(originalPublishedAt);
        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    @Test
    void unpublishingKeepsPublishedAtAndPublishesEvent() {
        LocalDateTime originalPublishedAt = LocalDateTime.now().minusDays(3);
        Shuoshuo published = shuoshuo(Shuoshuo.Visibility.PUBLIC, originalPublishedAt);
        when(shuoshuoRepository.findById(1L)).thenReturn(Optional.of(published));
        when(shuoshuoRepository.saveAndFlush(any(Shuoshuo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShuoshuoAdminResponse response = shuoshuoService.unpublish(1L);

        assertThat(response.visibility()).isEqualTo("PRIVATE");
        // 撤回保留发布时间：重新发布时回到原来的时间线位置
        assertThat(response.publishedAt()).isEqualTo(originalPublishedAt);
        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    // ── 编辑 ──────────────────────────────────────────────

    @Test
    void editingPublicShuoshuoPublishesEvent() {
        Shuoshuo published = shuoshuo(Shuoshuo.Visibility.PUBLIC, LocalDateTime.now());
        when(shuoshuoRepository.findById(1L)).thenReturn(Optional.of(published));
        when(shuoshuoRepository.saveAndFlush(any(Shuoshuo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShuoshuoAdminResponse response = shuoshuoService.update(1L, new ShuoshuoUpdateRequest("改过的内容"));

        assertThat(response.content()).isEqualTo("改过的内容");
        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    @Test
    void editingDraftDoesNotPublishEvent() {
        Shuoshuo draft = shuoshuo(Shuoshuo.Visibility.PRIVATE, null);
        when(shuoshuoRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(shuoshuoRepository.saveAndFlush(any(Shuoshuo.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        shuoshuoService.update(1L, new ShuoshuoUpdateRequest("改过的内容"));

        // 草稿的编辑不触发任何外部通知
        verifyNoInteractions(eventPublisher);
    }

    // ── 删除 ──────────────────────────────────────────────

    @Test
    void deletingShuoshuoPublishesEvent() {
        Shuoshuo published = shuoshuo(Shuoshuo.Visibility.PUBLIC, LocalDateTime.now());
        when(shuoshuoRepository.findById(1L)).thenReturn(Optional.of(published));

        shuoshuoService.delete(1L);

        verify(shuoshuoRepository).delete(published);
        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    @Test
    void deletingUnknownShuoshuoIsRejected() {
        when(shuoshuoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shuoshuoService.delete(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        verify(shuoshuoRepository, never()).delete(any(Shuoshuo.class));
    }

    @Test
    void updatingUnknownShuoshuoIsRejected() {
        when(shuoshuoRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shuoshuoService.update(404L, new ShuoshuoUpdateRequest("内容")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ── 列表 ──────────────────────────────────────────────

    @Test
    void publicListOnlyQueriesPublicAndSortsByPublishedAtDesc() {
        Shuoshuo published = shuoshuo(Shuoshuo.Visibility.PUBLIC, LocalDateTime.now());
        when(shuoshuoRepository.findByVisibility(eq(Shuoshuo.Visibility.PUBLIC), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(published)));

        var response = shuoshuoService.listPublic(new ShuoshuoQuery());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(shuoshuoRepository).findByVisibility(eq(Shuoshuo.Visibility.PUBLIC), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        Sort.Order order = pageable.getSort().getOrderFor("publishedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().content()).isEqualTo("内容");
    }

    @Test
    void adminListFiltersByVisibility() {
        Shuoshuo draft = shuoshuo(Shuoshuo.Visibility.PRIVATE, null);
        when(shuoshuoRepository.findByVisibility(eq(Shuoshuo.Visibility.PRIVATE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(draft)));

        ShuoshuoQuery query = new ShuoshuoQuery();
        query.setVisibility("PRIVATE");
        var response = shuoshuoService.listForAdmin(query);

        verify(shuoshuoRepository).findByVisibility(eq(Shuoshuo.Visibility.PRIVATE), any(Pageable.class));
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().visibility()).isEqualTo("PRIVATE");
    }

    @Test
    void adminListWithoutFilterUsesRepositoryDefaultOrder() {
        when(shuoshuoRepository.findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shuoshuo(Shuoshuo.Visibility.PUBLIC, LocalDateTime.now()))));

        var response = shuoshuoService.listForAdmin(new ShuoshuoQuery());

        verify(shuoshuoRepository).findAllByOrderByCreatedAtDescIdDesc(any(Pageable.class));
        assertThat(response.items()).hasSize(1);
    }

    // ── 辅助 ──────────────────────────────────────────────

    /** 取出本次发布的内容变更原因。 */
    private List<String> publishedReasons() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        Object event = captor.getValue();
        assertThat(event).isInstanceOf(ContentChangedEvent.class);
        return ((ContentChangedEvent) event).reasons();
    }

    private Shuoshuo shuoshuo(Shuoshuo.Visibility visibility, LocalDateTime publishedAt) {
        Shuoshuo shuoshuo = new Shuoshuo();
        shuoshuo.setId(1L);
        shuoshuo.setContent("内容");
        shuoshuo.setVisibility(visibility);
        shuoshuo.setPublishedAt(publishedAt);
        shuoshuo.setCreatedAt(LocalDateTime.now().minusDays(1));
        shuoshuo.setUpdatedAt(LocalDateTime.now());
        return shuoshuo;
    }
}
