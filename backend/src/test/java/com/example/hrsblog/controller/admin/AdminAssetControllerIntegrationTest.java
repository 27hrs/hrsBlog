package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.StaticAsset;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 静态资源接口集成测试：上传登记 → 列表 → 删除，以及类型/大小/伪装文件三类拒绝。
 */
class AdminAssetControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ASSETS_URL = "/api/admin/assets";
    private static final String BASE_URL = "https://cdn.test";
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private AdminUser admin;
    private String authorization;

    @BeforeEach
    void setUpAdmin() {
        admin = createAdmin("asset-admin");
        authorization = bearer(admin);
    }

    @Test
    void rejectsUploadWithoutToken() throws Exception {
        mockMvc.perform(multipart(ASSETS_URL).file(pngFile("unauthorized.png")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void uploadsPngAndPersistsLinkWithFileOnDisk() throws Exception {
        mockMvc.perform(multipart(ASSETS_URL).file(pngFile("pixel.png")).header(AUTH_HEADER, authorization))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith(BASE_URL + "/assets/image/")))
                .andExpect(jsonPath("$.data.originalName").value("pixel.png"))
                .andExpect(jsonPath("$.data.mimeType").value("image/png"))
                .andExpect(jsonPath("$.data.size").value(PNG.length))
                .andExpect(jsonPath("$.data.kind").value("IMAGE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.hash").value(org.hamcrest.Matchers.hasLength(64)))
                .andExpect(jsonPath("$.data.createdAt").exists());

        StaticAsset asset = onlyAsset();
        assertThat(asset.getStorageKey()).endsWith(".png");
        assertThat(asset.getUrl()).isEqualTo(BASE_URL + "/assets/" + asset.getStorageKey());
        assertThat(storageRoot().resolve(asset.getStorageKey())).hasBinaryContent(PNG);
    }

    @Test
    void identicalContentIsDeduplicated() throws Exception {
        mockMvc.perform(multipart(ASSETS_URL).file(pngFile("first.png")).header(AUTH_HEADER, authorization))
                .andExpect(status().isCreated());
        Long firstId = onlyAsset().getId();

        mockMvc.perform(multipart(ASSETS_URL).file(pngFile("second.png")).header(AUTH_HEADER, authorization))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(firstId))
                .andExpect(jsonPath("$.data.originalName").value("first.png"));

        assertThat(staticAssetRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "payload.exe", "application/octet-stream", PNG);

        mockMvc.perform(multipart(ASSETS_URL).file(file).header(AUTH_HEADER, authorization))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(ErrorCode.ASSET_TYPE_NOT_ALLOWED.code()));
        assertThat(staticAssetRepository.count()).isZero();
    }

    @Test
    void rejectsContentTypeThatDoesNotMatchExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/jpeg", PNG);

        mockMvc.perform(multipart(ASSETS_URL).file(file).header(AUTH_HEADER, authorization))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(ErrorCode.ASSET_TYPE_NOT_ALLOWED.code()));
    }

    @Test
    void rejectsFakeImageDisguisedAsPng() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "shell.png", "image/png",
                "<?php system($_GET['c']); ?>".getBytes());

        mockMvc.perform(multipart(ASSETS_URL).file(file).header(AUTH_HEADER, authorization))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(ErrorCode.ASSET_TYPE_NOT_ALLOWED.code()));
        assertThat(staticAssetRepository.count()).isZero();
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() throws Exception {
        byte[] oversized = new byte[(int) org.springframework.util.unit.DataSize.parse("64KB").toBytes() + 1];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);
        MockMultipartFile file = new MockMultipartFile("file", "huge.png", "image/png", oversized);

        mockMvc.perform(multipart(ASSETS_URL).file(file).header(AUTH_HEADER, authorization))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.ASSET_TOO_LARGE.code()));
    }

    @Test
    void listsAssetsWithPaginationAndStatusFilter() throws Exception {
        uploadPng("listed.png");

        mockMvc.perform(get(ASSETS_URL).header(AUTH_HEADER, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].originalName").value("listed.png"));

        mockMvc.perform(get(ASSETS_URL).param("status", "DELETED").header(AUTH_HEADER, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get(ASSETS_URL).param("status", "BROKEN").header(AUTH_HEADER, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").exists());
    }

    @Test
    void softDeleteMarksStatusAndKeepsFile() throws Exception {
        uploadPng("soft.png");
        StaticAsset asset = onlyAsset();
        Path file = storageRoot().resolve(asset.getStorageKey());

        mockMvc.perform(delete(ASSETS_URL + "/" + asset.getId()).header(AUTH_HEADER, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(staticAssetRepository.findById(asset.getId()).orElseThrow().getStatus())
                .isEqualTo(StaticAsset.AssetStatus.DELETED);
        assertThat(Files.exists(file)).isTrue();

        mockMvc.perform(get(ASSETS_URL).param("status", "DELETED").header(AUTH_HEADER, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 幂等：重复逻辑删除不报错
        mockMvc.perform(delete(ASSETS_URL + "/" + asset.getId()).header(AUTH_HEADER, authorization))
                .andExpect(status().isOk());
    }

    @Test
    void hardDeleteRemovesRecordAndFile() throws Exception {
        uploadPng("hard.png");
        StaticAsset asset = onlyAsset();
        Path file = storageRoot().resolve(asset.getStorageKey());
        assertThat(Files.exists(file)).isTrue();

        mockMvc.perform(delete(ASSETS_URL + "/" + asset.getId()).param("hard", "true")
                        .header(AUTH_HEADER, authorization))
                .andExpect(status().isOk());

        assertThat(staticAssetRepository.findById(asset.getId())).isEmpty();
        assertThat(Files.exists(file)).isFalse();

        mockMvc.perform(delete(ASSETS_URL + "/" + asset.getId()).param("hard", "true")
                        .header(AUTH_HEADER, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ASSET_NOT_FOUND.code()));
    }

    private void uploadPng(String fileName) throws Exception {
        mockMvc.perform(multipart(ASSETS_URL).file(pngFile(fileName)).header(AUTH_HEADER, authorization))
                .andExpect(status().isCreated());
    }

    private MockMultipartFile pngFile(String fileName) {
        return new MockMultipartFile("file", fileName, "image/png", PNG);
    }

    private StaticAsset onlyAsset() {
        assertThat(staticAssetRepository.count()).isEqualTo(1);
        return staticAssetRepository.findAll().getFirst();
    }
}
