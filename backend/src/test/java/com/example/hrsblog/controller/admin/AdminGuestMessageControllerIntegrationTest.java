package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.GuestMessage;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员留言接口集成测试：鉴权、状态筛选、审核流转、回复与删除。
 *
 * <p>核心安全断言：只有管理端响应里能看到 email、ipAddress 与 userAgent。
 */
class AdminGuestMessageControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String GUEST_MESSAGES_URL = "/api/admin/guest-messages";

    private AdminUser admin;
    private String authorization;

    @BeforeEach
    void setUpAdmin() {
        admin = createAdmin("guest-message-admin");
        authorization = bearer(admin);
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get(GUEST_MESSAGES_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void rejectsRequestWithMalformedToken() throws Exception {
        mockMvc.perform(get(GUEST_MESSAGES_URL).header(AUTH_HEADER, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsGuestMessagesWithPrivateFields() throws Exception {
        createGuestMessage("留言甲", "12345678@qq.com", GuestMessage.MessageStatus.PENDING);
        createGuestMessage("留言乙", "someone@example.test", GuestMessage.MessageStatus.APPROVED);

        mockMvc.perform(get(GUEST_MESSAGES_URL).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].email").exists())
                .andExpect(jsonPath("$.data.items[0].ipAddress").value("203.0.113.20"))
                .andExpect(jsonPath("$.data.items[0].userAgent").exists())
                .andExpect(jsonPath("$.data.items[0].status").exists());
    }

    @Test
    void filtersByStatus() throws Exception {
        createGuestMessage("待审留言", null, GuestMessage.MessageStatus.PENDING);
        createGuestMessage("已通过留言", null, GuestMessage.MessageStatus.APPROVED);

        mockMvc.perform(get(GUEST_MESSAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("待审留言"));

        mockMvc.perform(get(GUEST_MESSAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("已通过留言"));
    }

    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get(GUEST_MESSAGES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()));
    }

    @Test
    void approvesGuestMessageAndMakesItVisibleToVisitors() throws Exception {
        GuestMessage pending = createGuestMessage("留言", "12345678@qq.com",
                GuestMessage.MessageStatus.PENDING);

        mockMvc.perform(post(GUEST_MESSAGES_URL + "/" + pending.getId() + "/review")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/visitors/guest-messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("留言"));
    }

    @Test
    void rejectsGuestMessageAndKeepsItHiddenFromVisitors() throws Exception {
        GuestMessage pending = createGuestMessage("留言", null, GuestMessage.MessageStatus.PENDING);

        mockMvc.perform(post(GUEST_MESSAGES_URL + "/" + pending.getId() + "/review")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/visitors/guest-messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void rejectsInvalidReviewStatus() throws Exception {
        GuestMessage pending = createGuestMessage("留言", null, GuestMessage.MessageStatus.PENDING);

        mockMvc.perform(post(GUEST_MESSAGES_URL + "/" + pending.getId() + "/review")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.status").exists());
    }

    @Test
    void reviewsUnknownGuestMessageAsNotFound() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL + "/9999/review")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.GUEST_MESSAGE_NOT_FOUND.code()));
    }

    @Test
    void repliesToGuestMessageAndShowsReplyToVisitors() throws Exception {
        GuestMessage approved = createGuestMessage("留言", null, GuestMessage.MessageStatus.APPROVED);

        mockMvc.perform(put(GUEST_MESSAGES_URL + "/" + approved.getId() + "/reply")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"谢谢你的留言！\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reply.content").value("谢谢你的留言！"));

        mockMvc.perform(get("/api/visitors/guest-messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].replies.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].replies[0].content").value("谢谢你的留言！"));
    }

    @Test
    void editingReplyOverwritesThePreviousOne() throws Exception {
        GuestMessage approved = createGuestMessage("留言", null, GuestMessage.MessageStatus.APPROVED);

        mockMvc.perform(put(GUEST_MESSAGES_URL + "/" + approved.getId() + "/reply")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"第一条回复\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put(GUEST_MESSAGES_URL + "/" + approved.getId() + "/reply")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"改过的回复\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reply.content").value("改过的回复"));

        assertThat(messageReplyRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsBlankReplyContent() throws Exception {
        GuestMessage approved = createGuestMessage("留言", null, GuestMessage.MessageStatus.APPROVED);

        mockMvc.perform(put(GUEST_MESSAGES_URL + "/" + approved.getId() + "/reply")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.content").exists());
    }

    @Test
    void deletesGuestMessageAndItsReply() throws Exception {
        GuestMessage approved = createGuestMessage("留言", null, GuestMessage.MessageStatus.APPROVED);
        createReply(approved, admin, "站长回复");

        mockMvc.perform(delete(GUEST_MESSAGES_URL + "/" + approved.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(guestMessageRepository.findById(approved.getId())).isEmpty();
        assertThat(messageReplyRepository.count()).isZero();

        mockMvc.perform(get(GUEST_MESSAGES_URL + "/" + approved.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.GUEST_MESSAGE_NOT_FOUND.code()));
    }
}
