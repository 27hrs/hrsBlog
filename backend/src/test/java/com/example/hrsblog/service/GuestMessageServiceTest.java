package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.config.GuestMessageProperties;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.GuestMessage;
import com.example.hrsblog.domain.MessageReply;
import com.example.hrsblog.dto.GuestMessageReplyRequest;
import com.example.hrsblog.dto.GuestMessageReviewRequest;
import com.example.hrsblog.dto.GuestMessageSubmitRequest;
import com.example.hrsblog.dto.SettingValuesResponse;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.repository.GuestMessageRepository;
import com.example.hrsblog.repository.MessageReplyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 留言服务单元测试：限频、审核、回复 upsert 与删除顺序。
 *
 * <p>与友链的关键差异：留言是纯运行时数据，审核 / 回复 / 删除都不触发任何外部通知，
 * 因此这里既不需要事件发布器，也不需要断言埋点。
 */
@ExtendWith(MockitoExtension.class)
class GuestMessageServiceTest {

    @Mock
    private GuestMessageRepository guestMessageRepository;

    @Mock
    private MessageReplyRepository messageReplyRepository;

    @Mock
    private AdminUserRepository adminUserRepository;

    @Mock
    private SiteSettingService siteSettingService;

    @Mock
    private EntityManager entityManager;

    private GuestMessageService guestMessageService;

    @BeforeEach
    void setUp() {
        guestMessageService = new GuestMessageService(guestMessageRepository,
                messageReplyRepository, adminUserRepository,
                new GuestMessageProperties(5, true), siteSettingService, entityManager);
    }

    // ── 提交 ──────────────────────────────────────────────

    @Test
    void submittingBeyondDailyLimitIsRejected() {
        when(guestMessageRepository.countByIpAddressAndCreatedAtGreaterThanEqual(anyString(), any()))
                .thenReturn(5L);

        assertThatThrownBy(() -> guestMessageService.submit(submitRequest(), "203.0.113.1", "JUnit"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GUEST_MESSAGE_RATE_LIMITED);

        verify(guestMessageRepository, never()).saveAndFlush(any());
    }

    @Test
    void submittingStoresMessageAsPending() {
        when(guestMessageRepository.countByIpAddressAndCreatedAtGreaterThanEqual(anyString(), any()))
                .thenReturn(0L);
        when(guestMessageRepository.saveAndFlush(any(GuestMessage.class)))
                .thenAnswer(invocation -> {
                    GuestMessage message = invocation.getArgument(0);
                    message.setId(7L);
                    return message;
                });

        guestMessageService.submit(submitRequest(), "203.0.113.1", "JUnit/1.0");

        verify(guestMessageRepository).saveAndFlush(any(GuestMessage.class));
    }

    // ── 审核 ──────────────────────────────────────────────

    @Test
    void approvingUnknownGuestMessageIsRejected() {
        when(guestMessageRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestMessageService.review(404L, new GuestMessageReviewRequest("APPROVED")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GUEST_MESSAGE_NOT_FOUND);
    }

    // ── 回复 ──────────────────────────────────────────────

    @Test
    void replyingTwiceKeepsASingleReply() {
        GuestMessage message = guestMessage(GuestMessage.MessageStatus.APPROVED);
        AdminUser admin = admin();

        when(guestMessageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(messageReplyRepository.findByMessageId(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(reply(1L, message, admin, "旧回复")));
        when(messageReplyRepository.saveAndFlush(any(MessageReply.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        guestMessageService.reply(1L, new GuestMessageReplyRequest("新回复"), "admin");
        guestMessageService.reply(1L, new GuestMessageReplyRequest("新回复"), "admin");

        verify(messageReplyRepository, times(2)).saveAndFlush(any(MessageReply.class));
    }

    @Test
    void replyingToUnknownGuestMessageIsRejected() {
        when(guestMessageRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestMessageService.reply(404L, new GuestMessageReplyRequest("回复"), "admin"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GUEST_MESSAGE_NOT_FOUND);
    }

    // ── 删除 ──────────────────────────────────────────────

    @Test
    void deletingGuestMessageRemovesReplyFirst() {
        GuestMessage message = guestMessage(GuestMessage.MessageStatus.APPROVED);
        when(guestMessageRepository.findById(1L)).thenReturn(Optional.of(message));
        when(messageReplyRepository.deleteByMessageId(1L)).thenReturn(1L);

        guestMessageService.delete(1L);

        InOrder order = inOrder(messageReplyRepository, entityManager);
        order.verify(messageReplyRepository).deleteByMessageId(1L);
        order.verify(entityManager).flush();
        verify(guestMessageRepository).delete(message);
    }

    @Test
    void deletingUnknownGuestMessageIsRejected() {
        when(guestMessageRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guestMessageService.delete(404L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.GUEST_MESSAGE_NOT_FOUND);
    }

    // ── 每页数量 ──────────────────────────────────────────

    @Test
    void pageSizeFallsBackToConfiguredValue() {
        when(siteSettingService.getPublic())
                .thenReturn(new SettingValuesResponse(Map.of("guestbookPageSize", 7)));
        stubEmptyPage();

        assertThat(guestMessageService.listApproved(0, null).size()).isEqualTo(7);
    }

    @Test
    void pageSizeFallsBackToDefaultWhenSettingIsMissing() {
        when(siteSettingService.getPublic()).thenReturn(new SettingValuesResponse(Map.of()));
        stubEmptyPage();

        assertThat(guestMessageService.listApproved(0, null).size())
                .isEqualTo(GuestMessageService.DEFAULT_PAGE_SIZE);
    }

    @Test
    void explicitPageSizeIsClampedToLegalRange() {
        stubEmptyPage();

        assertThat(guestMessageService.listApproved(0, 999).size()).isEqualTo(GuestMessageService.MAX_PAGE_SIZE);
        assertThat(guestMessageService.listApproved(0, 0).size()).isEqualTo(1);
    }

    // ── 辅助 ──────────────────────────────────────────────

    private void stubEmptyPage() {
        // 用请求里的 Pageable 造空页，这样 PageResponse 里的 size 与服务层算出的每页条数一致
        when(guestMessageRepository.findByStatus(any(), any())).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            return new PageImpl<>(List.of(), pageable, 0);
        });
    }

    private GuestMessageSubmitRequest submitRequest() {
        return new GuestMessageSubmitRequest("湖边读者", "12345678@qq.com", "路过留一句话。");
    }

    private AdminUser admin() {
        AdminUser admin = new AdminUser();
        admin.setId(1L);
        admin.setUsername("admin");
        return admin;
    }

    private GuestMessage guestMessage(GuestMessage.MessageStatus status) {
        GuestMessage message = new GuestMessage();
        message.setId(1L);
        message.setNickname("湖边读者");
        message.setEmail("12345678@qq.com");
        message.setContent("路过留一句话。");
        message.setStatus(status);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        return message;
    }

    private MessageReply reply(Long id, GuestMessage message, AdminUser admin, String content) {
        MessageReply reply = new MessageReply();
        reply.setId(id);
        reply.setMessage(message);
        reply.setAdminUser(admin);
        reply.setContent(content);
        reply.setCreatedAt(LocalDateTime.now());
        return reply;
    }
}
