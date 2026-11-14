package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.StaticAsset;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 静态资源读取链路集成测试：上传登记后可通过 {@code /assets/**} 匿名读取；
 * 资源不存在返回 404，且不能通过 {@code ..} 跳出存储目录。
 */
class StaticResourceServingTest extends AbstractIntegrationTest {

    private static final String ASSETS_URL = "/api/admin/assets";
    private static final String BASE_URL = "https://cdn.test";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private String authorization;

    @BeforeEach
    void setUpAdmin() {
        AdminUser admin = createAdmin("serving-admin");
        authorization = bearer(admin);
    }

    @Test
    void uploadedAssetIsPubliclyReadableWithoutToken() throws Exception {
        mockMvc.perform(multipart(ASSETS_URL).file(png("public.png")).header(AUTH_HEADER, authorization))
                .andExpect(status().isCreated());

        StaticAsset asset = staticAssetRepository.findAll().getFirst();
        String publicPath = asset.getUrl().substring(BASE_URL.length());

        mockMvc.perform(get(publicPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void missingAssetReturnsNotFound() throws Exception {
        mockMvc.perform(get("/assets/image/00/not-exists.png"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.code()));
    }

    @Test
    void pathTraversalCannotEscapeStorageDirectory() throws Exception {
        int status = mockMvc.perform(get("/assets/../application.yaml"))
                .andReturn().getResponse().getStatus();

        assertThat(status).as("目录穿越请求必须被拒绝").isBetween(400, 499);
        assertThat(mockMvc.perform(get("/assets/../application.yaml")).andReturn()
                .getResponse().getContentAsString()).doesNotContain("spring:");
    }

    private MockMultipartFile png(String fileName) {
        return new MockMultipartFile("file", fileName, "image/png", PNG);
    }
}
