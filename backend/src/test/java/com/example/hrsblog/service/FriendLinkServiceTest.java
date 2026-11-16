package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.config.FriendLinkProperties;
import com.example.hrsblog.domain.FriendLink;
import com.example.hrsblog.dto.FriendLinkAdminUpdateRequest;
import com.example.hrsblog.dto.FriendLinkReviewRequest;
import com.example.hrsblog.dto.FriendLinkSubmitRequest;
import com.example.hrsblog.event.ContentChangedEvent;
import com.example.hrsblog.repository.FriendLinkRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 友链服务单元测试：审核 / 编辑 / 删除的埋点规则，以及提交时的限频与查重。
 *
 * <p>埋点规则（与产品约定一致）：
 * <ul>
 *   <li>访客提交申请 → 不触发；</li>
 *   <li>审核通过、已通过被改判为拒绝 → 触发 {@code friend_link_updated}；</li>
 *   <li>待审友链被编辑 / 删除 → 不触发；</li>
 *   <li>已通过友链被编辑 / 删除 → 触发 {@code friend_link_updated}。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FriendLinkServiceTest {

    @Mock
    private FriendLinkRepository friendLinkRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FriendLinkService friendLinkService;

    @BeforeEach
    void setUp() {
        friendLinkService = new FriendLinkService(friendLinkRepository,
                new FriendLinkProperties(5, true), entityManager, eventPublisher);
    }

    // ── 提交 ──────────────────────────────────────────────

    @Test
    void submittingFriendLinkDoesNotPublishEvent() {
        when(friendLinkRepository.countBySubmittedIpAndSubmittedAtGreaterThanEqual(anyString(), any()))
                .thenReturn(0L);
        when(friendLinkRepository.existsBySiteUrlAndStatus(anyString(), any())).thenReturn(false);
        when(friendLinkRepository.saveAndFlush(any(FriendLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        friendLinkService.submit(submitRequest(), "203.0.113.1");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void submittingBeyondDailyLimitIsRejected() {
        when(friendLinkRepository.countBySubmittedIpAndSubmittedAtGreaterThanEqual(anyString(), any()))
                .thenReturn(5L);

        assertThatThrownBy(() -> friendLinkService.submit(submitRequest(), "203.0.113.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FRIEND_LINK_RATE_LIMITED);

        verify(friendLinkRepository, never()).saveAndFlush(any());
    }

    @Test
    void submittingDuplicatePendingApplicationIsRejected() {
        when(friendLinkRepository.countBySubmittedIpAndSubmittedAtGreaterThanEqual(anyString(), any()))
                .thenReturn(0L);
        when(friendLinkRepository.existsBySiteUrlAndStatus(anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> friendLinkService.submit(submitRequest(), "203.0.113.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FRIEND_LINK_DUPLICATE);

        verify(friendLinkRepository, never()).saveAndFlush(any());
    }

    // ── 审核 ──────────────────────────────────────────────

    @Test
    void approvingFriendLinkPublishesEvent() {
        when(friendLinkRepository.findById(1L)).thenReturn(Optional.of(friendLink(FriendLink.FriendLinkStatus.PENDING)));
        when(friendLinkRepository.saveAndFlush(any(FriendLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        friendLinkService.review(1L, new FriendLinkReviewRequest("APPROVED"));

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_FRIEND_LINK_UPDATED);
    }

    @Test
    void rejectingPendingFriendLinkDoesNotPublishEvent() {
        when(friendLinkRepository.findById(1L)).thenReturn(Optional.of(friendLink(FriendLink.FriendLinkStatus.PENDING)));
        when(friendLinkRepository.saveAndFlush(any(FriendLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        friendLinkService.review(1L, new FriendLinkReviewRequest("REJECTED"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void rejectingAlreadyApprovedFriendLinkPublishesEvent() {
        when(friendLinkRepository.findById(1L)).thenReturn(Optional.of(friendLink(FriendLink.FriendLinkStatus.APPROVED)));
        when(friendLinkRepository.saveAndFlush(any(FriendLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        friendLinkService.review(1L, new FriendLinkReviewRequest("REJECTED"));

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_FRIEND_LINK_UPDATED);
    }

    // ── 编辑与删除 ────────────────────────────────────────

    @Test
    void updatingApprovedFriendLinkPublishesEvent() {
        when(friendLinkRepository.findById(1L)).thenReturn(Optional.of(friendLink(FriendLink.FriendLinkStatus.APPROVED)));
        when(friendLinkRepository.saveAndFlush(any(FriendLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        friendLinkService.update(1L, updateRequest());

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_FRIEND_LINK_UPDATED);
    }

    @Test
    void updatingPendingFriendLinkDoesNotPublishEvent() {
        when(friendLinkRepository.findById(1L)).thenReturn(Optional.of(friendLink(FriendLink.FriendLinkStatus.PENDING)));
        when(friendLinkRepository.saveAndFlush(any(FriendLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        friendLinkService.update(1L, updateRequest());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deletingApprovedFriendLinkPublishesEvent() {
        when(friendLinkRepository.findById(1L)).thenReturn(Optional.of(friendLink(FriendLink.FriendLinkStatus.APPROVED)));

        friendLinkService.delete(1L);

        verify(friendLinkRepository).delete(any(FriendLink.class));
        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_FRIEND_LINK_UPDATED);
    }

    @Test
    void deletingPendingFriendLinkDoesNotPublishEvent() {
        when(friendLinkRepository.findById(1L)).thenReturn(Optional.of(friendLink(FriendLink.FriendLinkStatus.PENDING)));

        friendLinkService.delete(1L);

        verify(friendLinkRepository).delete(any(FriendLink.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void deletingUnknownFriendLinkIsRejected() {
        when(friendLinkRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> friendLinkService.delete(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FRIEND_LINK_NOT_FOUND);
    }

    // ── 辅助 ──────────────────────────────────────────────

    private java.util.List<String> publishedReasons() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        Object event = captor.getValue();
        assertThat(event).isInstanceOf(ContentChangedEvent.class);
        return ((ContentChangedEvent) event).reasons();
    }

    private FriendLink friendLink(FriendLink.FriendLinkStatus status) {
        FriendLink friendLink = new FriendLink();
        friendLink.setId(1L);
        friendLink.setSiteName("站点");
        friendLink.setSiteUrl("https://site.test");
        friendLink.setLinkPageUrl("https://site.test/links");
        friendLink.setDescription("简介");
        friendLink.setContact("someone@site.test");
        friendLink.setStatus(status);
        friendLink.setSortOrder(0);
        friendLink.setSubmittedIp("203.0.113.1");
        friendLink.setSubmittedAt(LocalDateTime.now());
        return friendLink;
    }

    private FriendLinkSubmitRequest submitRequest() {
        return new FriendLinkSubmitRequest("站点", "https://site.test", "https://site.test/links",
                null, "简介", "someone@site.test");
    }

    private FriendLinkAdminUpdateRequest updateRequest() {
        return new FriendLinkAdminUpdateRequest("新名称", "https://site.test", "https://site.test/links",
                null, "新简介", "someone@site.test", 1);
    }
}
