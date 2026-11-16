package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.SystemLog;
import com.example.hrsblog.repository.SystemLogRepository;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通用日志接口集成测试：鉴权、分页、type / status 筛选。
 */
class AdminSystemLogControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String LOGS_URL = "/api/admin/logs";

    @Autowired
    private SystemLogRepository systemLogRepository;

    private AdminUser admin;
    private String authorization;

    @BeforeEach
    void setUpAdmin() {
        admin = createAdmin("log-admin");
        authorization = bearer(admin);
    }

    @Test
    void rejectsQueryWithoutToken() throws Exception {
        mockMvc.perform(get(LOGS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void returnsPagedLogsNewestFirst() throws Exception {
        saveLog(SystemLog.LogStatus.SUCCESS, 1, "第一条");
        saveLog(SystemLog.LogStatus.FAILED, 2, "第二条");

        mockMvc.perform(get(LOGS_URL).param("page", "0").param("size", "10").header(AUTH_HEADER, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value("DEPLOY_NOTIFY"))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].attempt").value(2))
                .andExpect(jsonPath("$.data.items[0].message").value("第二条"))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists());
    }

    @Test
    void filtersByTypeAndStatus() throws Exception {
        saveLog(SystemLog.LogStatus.SUCCESS, 1, "成功的一条");
        saveLog(SystemLog.LogStatus.FAILED, 1, "失败的一条");

        mockMvc.perform(get(LOGS_URL).param("type", "DEPLOY_NOTIFY").param("status", "FAILED")
                        .header(AUTH_HEADER, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[0].message").value("失败的一条"));
    }

    @Test
    void rejectsUnknownStatusFilter() throws Exception {
        mockMvc.perform(get(LOGS_URL).param("status", "BROKEN").header(AUTH_HEADER, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()));
    }

    private SystemLog saveLog(SystemLog.LogStatus status, int attempt, String message) {
        SystemLog entry = new SystemLog();
        entry.setType("DEPLOY_NOTIFY");
        entry.setScene("blog-content-updated");
        entry.setStatus(status);
        entry.setAttempt(attempt);
        entry.setMessage(message);
        entry.setDetail("POST https://example.test/dispatch -> HTTP 204");
        SystemLog saved = systemLogRepository.saveAndFlush(entry);
        assertThat(saved.getId()).isNotNull();
        return saved;
    }
}
