package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.GuestMessage;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 访客留言接口集成测试：提交校验、按 IP 限频、公开列表的可见范围。
 *
 * <p>核心安全断言：访客接口的响应体里<b>绝不能出现 email / ipAddress / userAgent</b>
 * （它们只给管理员看）。
 */
class VisitorGuestMessageControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String GUEST_MESSAGES_URL = "/api/visitors/guest-messages";

    @Test
    void submitsGuestMessageAsPending() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("湖边读者", "12345678@qq.com", "路过留一句话。")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("湖边读者"))
                .andExpect(jsonPath("$.data.content").value("路过留一句话。"))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.replies.length()").value(0));

        assertThat(guestMessageRepository.count()).isEqualTo(1);
        GuestMessage saved = guestMessageRepository.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(GuestMessage.MessageStatus.PENDING);
        assertThat(saved.getEmail()).isEqualTo("12345678@qq.com");
        assertThat(saved.getIpAddress()).isNotBlank();
    }

    @Test
    void submitResponseNeverExposesPrivateFields() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("匿名", "someone@example.test", "内容")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.ipAddress").doesNotExist())
                .andExpect(jsonPath("$.data.userAgent").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist());
    }

    @Test
    void derivesQqAvatarFromEmail() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("QQ 用户", "12345678@qq.com", "内容")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.avatarUrl")
                        .value("https://q1.qlogo.cn/headimg_dl?dst_uin=12345678&spec=100"));
    }

    @Test
    void leavesAvatarEmptyForNonQqEmail() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("普通用户", "someone@example.test", "内容")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());
    }

    @Test
    void publicListNeverExposesPrivateFields() throws Exception {
        createGuestMessage("已通过留言", "12345678@qq.com", GuestMessage.MessageStatus.APPROVED);

        mockMvc.perform(get(GUEST_MESSAGES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("已通过留言"))
                .andExpect(jsonPath("$.data.items[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].ipAddress").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].userAgent").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].status").doesNotExist());
    }

    @Test
    void publicListOnlyContainsApprovedEntries() throws Exception {
        createGuestMessage("待审留言", null, GuestMessage.MessageStatus.PENDING);
        createGuestMessage("已通过留言", null, GuestMessage.MessageStatus.APPROVED);
        createGuestMessage("已拒绝留言", null, GuestMessage.MessageStatus.REJECTED);

        mockMvc.perform(get(GUEST_MESSAGES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].nickname").value("已通过留言"));
    }

    @Test
    void rejectsBlankNickname() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("", null, "内容")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.nickname").exists());
        assertThat(guestMessageRepository.count()).isZero();
    }

    @Test
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("湖边读者", null, "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.content").exists());
        assertThat(guestMessageRepository.count()).isZero();
    }

    @Test
    void rejectsInvalidEmailFormat() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("湖边读者", "not-an-email", "内容")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.email").exists());
        assertThat(guestMessageRepository.count()).isZero();
    }

    @Test
    void rejectsTooLongNickname() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("n".repeat(65), null, "内容")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.nickname").exists());
        assertThat(guestMessageRepository.count()).isZero();
    }

    @Test
    void rejectsTooLongContent() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("湖边读者", null, "c".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.content").exists());
        assertThat(guestMessageRepository.count()).isZero();
    }

    @Test
    void acceptsEmptyOptionalEmail() throws Exception {
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("湖边读者", "", "内容")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist());

        GuestMessage saved = guestMessageRepository.findAll().get(0);
        assertThat(saved.getEmail()).isNull();
    }

    @Test
    void rejectsOversizedBody() throws Exception {
        // 匿名写接口的请求体闸门：超大 body 在参数解析之前就被挡掉
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("湖边读者", null, "c".repeat(20_000))))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUEST_BODY_TOO_LARGE.code()));
        assertThat(guestMessageRepository.count()).isZero();
    }

    @Test
    void enforcesDailyLimitPerIp() throws Exception {
        // 测试配置把每日限额压到 2 次
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("读者一", null, "内容一")))
                .andExpect(status().isCreated());
        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("读者二", null, "内容二")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(GUEST_MESSAGES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("读者三", null, "内容三")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.GUEST_MESSAGE_RATE_LIMITED.code()));

        assertThat(guestMessageRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        mockMvc.perform(get(GUEST_MESSAGES_URL).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()));
    }

    private String submitJson(String nickname, String email, String content) {
        return """
                {
                  "nickname": %s,
                  "email": %s,
                  "content": %s
                }
                """.formatted(json(nickname), json(email), json(content));
    }

    private String json(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
