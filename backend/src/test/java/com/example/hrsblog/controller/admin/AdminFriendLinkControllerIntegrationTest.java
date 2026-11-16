package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.FriendLink;
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
 * 管理员友链接口集成测试：鉴权、状态筛选、审核流转、编辑与删除。
 *
 * <p>核心安全断言：只有管理端响应里能看到 contact 与 submittedIp。
 */
class AdminFriendLinkControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String FRIEND_LINKS_URL = "/api/admin/friend-links";

    private AdminUser admin;
    private String authorization;

    @BeforeEach
    void setUpAdmin() {
        admin = createAdmin("friend-link-admin");
        authorization = bearer(admin);
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get(FRIEND_LINKS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void rejectsRequestWithMalformedToken() throws Exception {
        mockMvc.perform(get(FRIEND_LINKS_URL).header(AUTH_HEADER, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsFriendLinksWithContactAndSubmittedIp() throws Exception {
        createFriendLink("站点甲", "https://a.test", FriendLink.FriendLinkStatus.PENDING);
        createFriendLink("站点乙", "https://b.test", FriendLink.FriendLinkStatus.APPROVED);

        mockMvc.perform(get(FRIEND_LINKS_URL).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].contact").exists())
                .andExpect(jsonPath("$.data.items[0].submittedIp").value("203.0.113.10"))
                .andExpect(jsonPath("$.data.items[0].status").exists());
    }

    @Test
    void filtersByStatus() throws Exception {
        createFriendLink("待审站点", "https://pending.test", FriendLink.FriendLinkStatus.PENDING);
        createFriendLink("已通过站点", "https://approved.test", FriendLink.FriendLinkStatus.APPROVED);

        mockMvc.perform(get(FRIEND_LINKS_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].siteName").value("待审站点"));

        mockMvc.perform(get(FRIEND_LINKS_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].siteName").value("已通过站点"));
    }

    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get(FRIEND_LINKS_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()));
    }

    @Test
    void approvesFriendLinkAndMakesItVisibleToVisitors() throws Exception {
        FriendLink pending = createFriendLink("站点", "https://approve.test", FriendLink.FriendLinkStatus.PENDING);

        mockMvc.perform(post(FRIEND_LINKS_URL + "/" + pending.getId() + "/review")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").exists());

        mockMvc.perform(get("/api/visitors/friend-links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].siteName").value("站点"));
    }

    @Test
    void rejectsFriendLinkAndKeepsItHiddenFromVisitors() throws Exception {
        FriendLink pending = createFriendLink("站点", "https://reject.test", FriendLink.FriendLinkStatus.PENDING);

        mockMvc.perform(post(FRIEND_LINKS_URL + "/" + pending.getId() + "/review")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mockMvc.perform(get("/api/visitors/friend-links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void rejectsInvalidReviewStatus() throws Exception {
        FriendLink pending = createFriendLink("站点", "https://bad-review.test", FriendLink.FriendLinkStatus.PENDING);

        mockMvc.perform(post(FRIEND_LINKS_URL + "/" + pending.getId() + "/review")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.status").exists());
    }

    @Test
    void reviewsUnknownFriendLinkAsNotFound() throws Exception {
        mockMvc.perform(post(FRIEND_LINKS_URL + "/9999/review")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.FRIEND_LINK_NOT_FOUND.code()));
    }

    @Test
    void updatesFriendLinkFields() throws Exception {
        FriendLink pending = createFriendLink("旧名称", "https://old.test", FriendLink.FriendLinkStatus.PENDING);

        mockMvc.perform(put(FRIEND_LINKS_URL + "/" + pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "siteName": "新名称",
                                  "siteUrl": "https://new.test",
                                  "linkPageUrl": "https://new.test/links",
                                  "avatarUrl": "https://new.test/avatar.png",
                                  "description": "新的简介",
                                  "contact": "new@new.test",
                                  "sortOrder": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteName").value("新名称"))
                .andExpect(jsonPath("$.data.siteUrl").value("https://new.test"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://new.test/avatar.png"))
                .andExpect(jsonPath("$.data.contact").value("new@new.test"))
                .andExpect(jsonPath("$.data.sortOrder").value(5));
    }

    @Test
    void rejectsUpdateWithNonHttpUrl() throws Exception {
        FriendLink pending = createFriendLink("站点", "https://update.test", FriendLink.FriendLinkStatus.PENDING);

        mockMvc.perform(put(FRIEND_LINKS_URL + "/" + pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "siteName": "站点",
                                  "siteUrl": "javascript:alert(1)",
                                  "linkPageUrl": "https://update.test/links"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.siteUrl").exists());
    }

    @Test
    void deletesFriendLink() throws Exception {
        FriendLink approved = createFriendLink("站点", "https://delete.test", FriendLink.FriendLinkStatus.APPROVED);

        mockMvc.perform(delete(FRIEND_LINKS_URL + "/" + approved.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(friendLinkRepository.findById(approved.getId())).isEmpty();

        mockMvc.perform(get(FRIEND_LINKS_URL + "/" + approved.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.FRIEND_LINK_NOT_FOUND.code()));
    }
}
