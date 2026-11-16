package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.FriendLink;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 访客友链接口集成测试：提交校验、按 IP 限频、重复申请、公开列表的可见范围。
 *
 * <p>核心安全断言：访客接口的响应体里<b>绝不能出现 contact</b>（联系方式只给管理员看）。
 */
class VisitorFriendLinkControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String FRIEND_LINKS_URL = "/api/visitors/friend-links";

    @Test
    void submitsFriendLinkAsPending() throws Exception {
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("雾湖手记", "https://lake.test", "https://lake.test/links",
                                "https://lake.test/avatar.png", "一个关于湖与代码的博客", "someone@lake.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.siteName").value("雾湖手记"))
                .andExpect(jsonPath("$.data.siteUrl").value("https://lake.test"))
                .andExpect(jsonPath("$.data.linkPageUrl").value("https://lake.test/links"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://lake.test/avatar.png"))
                .andExpect(jsonPath("$.data.description").value("一个关于湖与代码的博客"))
                .andExpect(jsonPath("$.data.id").exists());

        assertThat(friendLinkRepository.count()).isEqualTo(1);
        FriendLink saved = friendLinkRepository.findAll().get(0);
        assertThat(saved.getStatus()).isEqualTo(FriendLink.FriendLinkStatus.PENDING);
        assertThat(saved.getContact()).isEqualTo("someone@lake.test");
        assertThat(saved.getSubmittedIp()).isNotBlank();
    }

    @Test
    void submitResponseNeverExposesContact() throws Exception {
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点", "https://no-contact.test", "https://no-contact.test/links",
                                null, "简介", "secret@no-contact.test")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contact").doesNotExist());
    }

    @Test
    void publicListNeverExposesContact() throws Exception {
        createFriendLink("已通过站点", "https://approved.test", FriendLink.FriendLinkStatus.APPROVED);

        mockMvc.perform(get(FRIEND_LINKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].siteName").value("已通过站点"))
                .andExpect(jsonPath("$.data[0].contact").doesNotExist())
                .andExpect(jsonPath("$.data[0].submittedIp").doesNotExist());
    }

    @Test
    void publicListOnlyContainsApprovedEntries() throws Exception {
        createFriendLink("待审站点", "https://pending.test", FriendLink.FriendLinkStatus.PENDING);
        createFriendLink("已通过站点", "https://approved.test", FriendLink.FriendLinkStatus.APPROVED);
        createFriendLink("已拒绝站点", "https://rejected.test", FriendLink.FriendLinkStatus.REJECTED);

        mockMvc.perform(get(FRIEND_LINKS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].siteName").value("已通过站点"));
    }

    @Test
    void rejectsBlankSiteName() throws Exception {
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("", "https://blank.test", "https://blank.test/links", null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.siteName").exists());
        assertThat(friendLinkRepository.count()).isZero();
    }

    @Test
    void rejectsNonHttpSiteUrl() throws Exception {
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点", "javascript:alert(1)", "https://x.test/links", null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.siteUrl").exists());
        assertThat(friendLinkRepository.count()).isZero();
    }

    @Test
    void rejectsNonHttpLinkPageUrl() throws Exception {
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点", "https://x.test", "ftp://x.test/links", null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.linkPageUrl").exists());
        assertThat(friendLinkRepository.count()).isZero();
    }

    @Test
    void rejectsTooLongSiteName() throws Exception {
        String tooLong = "a".repeat(101);

        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson(tooLong, "https://x.test", "https://x.test/links", null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.siteName").exists());
        assertThat(friendLinkRepository.count()).isZero();
    }

    @Test
    void rejectsTooLongContact() throws Exception {
        String tooLong = "c".repeat(201);

        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点", "https://x.test", "https://x.test/links", null, null, tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.contact").exists());
        assertThat(friendLinkRepository.count()).isZero();
    }

    @Test
    void acceptsEmptyOptionalFields() throws Exception {
        // 空串视为「未填写」：可选字段允许传空字符串
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点", "https://x.test", "https://x.test/links", "", "", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.avatarUrl").doesNotExist())
                .andExpect(jsonPath("$.data.description").doesNotExist());

        FriendLink saved = friendLinkRepository.findAll().get(0);
        assertThat(saved.getAvatarUrl()).isNull();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getContact()).isNull();
    }

    @Test
    void rejectsOversizedBody() throws Exception {
        // 匿名写接口的请求体闸门：超大 body 在参数解析之前就被挡掉
        String huge = "x".repeat(20_000);

        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson(huge, "https://big.test", "https://big.test/links", null, null, null)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUEST_BODY_TOO_LARGE.code()));
        assertThat(friendLinkRepository.count()).isZero();
    }

    @Test
    void enforcesDailyLimitPerIp() throws Exception {
        // 测试配置把每日限额压到 2 次
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点一", "https://one.test", "https://one.test/links", null, null, null)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点二", "https://two.test", "https://two.test/links", null, null, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点三", "https://three.test", "https://three.test/links", null, null, null)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(ErrorCode.FRIEND_LINK_RATE_LIMITED.code()));

        assertThat(friendLinkRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectsDuplicatePendingApplicationForSameSite() throws Exception {
        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点", "https://dup.test", "https://dup.test/links", null, null, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(FRIEND_LINKS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitJson("站点", "https://dup.test", "https://dup.test/links", null, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.FRIEND_LINK_DUPLICATE.code()));

        assertThat(friendLinkRepository.count()).isEqualTo(1);
    }

    private String submitJson(String siteName, String siteUrl, String linkPageUrl,
                              String avatarUrl, String description, String contact) {
        return """
                {
                  "siteName": %s,
                  "siteUrl": %s,
                  "linkPageUrl": %s,
                  "avatarUrl": %s,
                  "description": %s,
                  "contact": %s
                }
                """.formatted(json(siteName), json(siteUrl), json(linkPageUrl),
                json(avatarUrl), json(description), json(contact));
    }

    private String json(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
