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
 * 登录接口集成测试：成功签发 JWT、失败统一 401、DISABLED 拒绝、参数校验与注入防护。
 */
class AdminAuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String LOGIN_URL = "/api/admin/auth/login";
    private static final String PASSWORD_URL = "/api/admin/auth/password";

    @Test
    void loginReturnsJwtAndUpdatesLastLoginAt() throws Exception {
        createAdmin("login-ok");

        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login-ok", ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.username").value("login-ok"))
                .andExpect(jsonPath("$.data.user.displayName").value("Admin login-ok"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash");
        assertThat(adminUserRepository.findByUsername("login-ok").orElseThrow().getLastLoginAt()).isNotNull();
    }

    @Test
    void issuedTokenCanAccessProtectedAdminEndpoints() throws Exception {
        AdminUser admin = createAdmin("token-admin");

        mockMvc.perform(get("/api/admin/assets").header(AUTH_HEADER, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void wrongPasswordReturnsUnifiedUnauthorized() throws Exception {
        createAdmin("wrong-pass-admin");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("wrong-pass-admin", "WrongPassword1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.message()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unknownAccountReturnsExactlyTheSameResponseAsWrongPassword() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ghost-admin", ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.message()));
    }

    @Test
    void disabledAccountIsRejectedWithUnauthorized() throws Exception {
        AdminUser admin = createAdmin("disabled-admin");
        admin.setStatus(AdminUser.AdminStatus.DISABLED);
        adminUserRepository.saveAndFlush(admin);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("disabled-admin", ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.message()));
    }

    @Test
    void shortPasswordIsRejectedWithFieldLevelValidationError() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("someone", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    void tooShortUsernameIsRejectedWithFieldLevelValidationError() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("ab", ADMIN_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.username").exists());
    }

    @Test
    void blankCredentialsAreRejected() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()));
    }

    @Test
    void sqlInjectionInUsernameCannotBypassAuthentication() throws Exception {
        createAdmin("injection-admin");
        long before = adminUserRepository.count();

        for (String payload : new String[]{"' OR 1=1 --", "admin'--", "'; DROP TABLE admin_user; --"}) {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson(payload, ADMIN_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.code()));
        }

        assertThat(adminUserRepository.count()).isEqualTo(before);
    }

    private String loginJson(String username, String password) {
        return "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(escape(username), escape(password));
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(put(PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(ADMIN_PASSWORD, "NewPassword456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void changePasswordWithWrongOldPasswordReturnsDedicatedCode() throws Exception {
        createAdmin("pwd-wrong-old");

        mockMvc.perform(put(PASSWORD_URL)
                        .header(AUTH_HEADER, bearer(adminUserRepository.findByUsername("pwd-wrong-old").orElseThrow()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("WrongPassword1", "NewPassword456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED_PASSWORD.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED_PASSWORD.message()));

        assertThat(passwordEncoder.matches(ADMIN_PASSWORD,
                adminUserRepository.findByUsername("pwd-wrong-old").orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void changePasswordIssuesNewTokenWhileOldTokenStaysValid() throws Exception {
        AdminUser admin = createAdmin("pwd-admin");
        String oldToken = bearer(admin);

        MvcResult result = mockMvc.perform(put(PASSWORD_URL)
                        .header(AUTH_HEADER, oldToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(ADMIN_PASSWORD, "NewPassword456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andReturn();

        String newToken = "Bearer " + JsonPath.<String>read(
                result.getResponse().getContentAsString(), "$.data.accessToken");

        // 旧 Token 在有效期内仍然可用（无状态 JWT，不做服务端吊销）
        mockMvc.perform(get("/api/admin/assets").header(AUTH_HEADER, oldToken))
                .andExpect(status().isOk());
        // 新 Token 立即可用
        mockMvc.perform(get("/api/admin/assets").header(AUTH_HEADER, newToken))
                .andExpect(status().isOk());
        // 新密码可以登录
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("pwd-admin", "NewPassword456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString());
        // 旧密码不再可用
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("pwd-admin", ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.code()));
    }

    @Test
    void changePasswordRejectsReusedPassword() throws Exception {
        AdminUser admin = createAdmin("pwd-reuse");

        mockMvc.perform(put(PASSWORD_URL)
                        .header(AUTH_HEADER, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(ADMIN_PASSWORD, ADMIN_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.message").value("新密码不能与原密码相同"));
    }

    @Test
    void changePasswordRejectsWeakNewPassword() throws Exception {
        AdminUser admin = createAdmin("pwd-weak");
        String token = bearer(admin);

        // 纯字母、没有数字
        mockMvc.perform(put(PASSWORD_URL)
                        .header(AUTH_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(ADMIN_PASSWORD, "onlyletters")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.newPassword").exists());

        // 长度不足
        mockMvc.perform(put(PASSWORD_URL)
                        .header(AUTH_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson(ADMIN_PASSWORD, "Sh0rt")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.newPassword").exists());
    }

    private String passwordJson(String oldPassword, String newPassword) {
        return "{\"oldPassword\":\"%s\",\"newPassword\":\"%s\"}"
                .formatted(escape(oldPassword), escape(newPassword));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
