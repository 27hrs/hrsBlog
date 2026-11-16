package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员账号维护集成测试：修改用户名接口的鉴权、校验、唯一性与改名后的连锁行为。
 *
 * <p>重点覆盖「用户名即 JWT subject」这一约束：改名后必须换用新 Token，
 * 旧用户名不能再登录，但账号本身（密码修改等）仍然完全可用。
 */
class AdminAccountIntegrationTest extends AbstractIntegrationTest {

    private static final String USERNAME_URL = "/api/admin/auth/username";
    private static final String PASSWORD_URL = "/api/admin/auth/password";
    private static final String LOGIN_URL = "/api/admin/auth/login";

    @Test
    void changeUsernameWithoutTokenIsUnauthorized() throws Exception {
        createAdmin("guard-admin");

        mockMvc.perform(put(USERNAME_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameJson("guarded-name")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));

        assertThat(adminUserRepository.findByUsername("guard-admin")).isPresent();
        assertThat(adminUserRepository.findByUsername("guarded-name")).isEmpty();
    }

    @Test
    void changeUsernameUpdatesAccountAndReturnsNewToken() throws Exception {
        AdminUser admin = createAdmin("old-name");

        MvcResult result = mockMvc.perform(put(USERNAME_URL)
                        .header(AUTH_HEADER, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameJson("new-name")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.username").value("new-name"))
                .andExpect(jsonPath("$.data.user.id").value(admin.getId()))
                .andReturn();

        // 数据库中的用户名已更新，旧用户名不再存在
        assertThat(adminUserRepository.findByUsername("new-name")).isPresent();
        assertThat(adminUserRepository.findByUsername("old-name")).isEmpty();

        String newToken = JsonPath.<String>read(result.getResponse().getContentAsString(), "$.data.accessToken");
        assertThat(newToken).isNotBlank();

        // 新令牌的 subject 必须是新用户名
        assertThat(jwtTokenProvider.parse(newToken).username()).isEqualTo("new-name");

        // 新令牌可以正常访问受保护接口
        mockMvc.perform(get("/api/admin/assets").header(AUTH_HEADER, "Bearer " + newToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void changeUsernameRejectsInvalidFormat() throws Exception {
        AdminUser admin = createAdmin("fmt-admin");
        String token = bearer(admin);

        // 太短、含非法字符、空串：全部返回字段级校验错误
        for (String invalid : new String[]{"ab", "has space", ""}) {
            mockMvc.perform(put(USERNAME_URL)
                            .header(AUTH_HEADER, token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(usernameJson(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                    .andExpect(jsonPath("$.data.newUsername").exists());
        }

        // 原用户名不受影响
        assertThat(adminUserRepository.findByUsername("fmt-admin")).isPresent();
    }

    @Test
    void changeUsernameRejectsTakenUsername() throws Exception {
        createAdmin("taken-name");
        AdminUser other = createAdmin("other-admin");

        mockMvc.perform(put(USERNAME_URL)
                        .header(AUTH_HEADER, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameJson("taken-name")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.USERNAME_CONFLICT.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.USERNAME_CONFLICT.message()));

        assertThat(adminUserRepository.findByUsername("other-admin")).isPresent();
    }

    @Test
    void changeUsernameAllowsKeepingTheSameUsername() throws Exception {
        AdminUser admin = createAdmin("same-name");

        mockMvc.perform(put(USERNAME_URL)
                        .header(AUTH_HEADER, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameJson("same-name")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.username").value("same-name"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        assertThat(adminUserRepository.findByUsername("same-name")).isPresent();
    }

    @Test
    void newUsernameCanLogInAndOldUsernameCannot() throws Exception {
        AdminUser admin = createAdmin("login-old");
        mockMvc.perform(put(USERNAME_URL)
                        .header(AUTH_HEADER, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameJson("login-new")))
                .andExpect(status().isOk());

        // 新用户名可以登录
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login-new", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.user.username").value("login-new"));

        // 旧用户名不再可用
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login-old", ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.code()));
    }

    @Test
    void changePasswordStillWorksAfterUsernameChange() throws Exception {
        AdminUser admin = createAdmin("pwd-old-name");
        MvcResult renameResult = mockMvc.perform(put(USERNAME_URL)
                        .header(AUTH_HEADER, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(usernameJson("pwd-new-name")))
                .andExpect(status().isOk())
                .andReturn();

        String newToken = JsonPath.<String>read(renameResult.getResponse().getContentAsString(), "$.data.accessToken");

        // 改名后用新令牌修改密码，账号依然完全可用
        mockMvc.perform(put(PASSWORD_URL)
                        .header(AUTH_HEADER, "Bearer " + newToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(ADMIN_PASSWORD, "NewPassword456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // 新密码 + 新用户名可以登录
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("pwd-new-name", "NewPassword456")))
                .andExpect(status().isOk());
    }

    @Test
    void changeUsernameRejectsMalformedBody() throws Exception {
        AdminUser admin = createAdmin("bad-body-admin");

        mockMvc.perform(put(USERNAME_URL)
                        .header(AUTH_HEADER, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.code()));
    }

    private String usernameJson(String newUsername) {
        return "{\"newUsername\":\"%s\"}".formatted(escape(newUsername));
    }

    private String loginJson(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(escape(username), escape(password));
    }

    private String passwordJson(String oldPassword, String newPassword) {
        return "{\"oldPassword\":\"%s\",\"newPassword\":\"%s\"}"
                .formatted(escape(oldPassword), escape(newPassword));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
