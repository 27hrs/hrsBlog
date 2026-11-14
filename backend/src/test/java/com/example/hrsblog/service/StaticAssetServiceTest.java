package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.config.StorageProperties;
import com.example.hrsblog.domain.StaticAsset;
import com.example.hrsblog.dto.AssetQuery;
import com.example.hrsblog.dto.AssetResponse;
import com.example.hrsblog.repository.StaticAssetRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 静态资源单元测试：类型/大小校验、内容去重、逻辑删除与物理删除。 */
@ExtendWith(MockitoExtension.class)
class StaticAssetServiceTest {

    /** 1x1 的合法 PNG，只用于通过扩展名 + Content-Type + 魔数三重校验。 */
    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private static final String BASE_URL = "https://cdn.test";
    private static final String URL_PREFIX = BASE_URL + "/assets/";

    @TempDir
    Path tempDir;

    @Mock
    private StaticAssetRepository staticAssetRepository;

    @Mock
    private EntityManager entityManager;

    private LocalStorageService storageService;
    private StaticAssetService staticAssetService;
    private StorageProperties storageProperties;

    @BeforeEach
    void setUp() {
        storageProperties = new StorageProperties(
                tempDir.toString(), BASE_URL + "/", DataSize.ofKilobytes(1), List.of("jpg", "jpeg", "png", "webp", "gif"));
        storageService = new LocalStorageService(storageProperties);
        staticAssetService = new StaticAssetService(staticAssetRepository, storageService, storageProperties, entityManager);
    }

    @Test
    void uploadsImageAndWritesFileToStorage() throws IOException {
        when(staticAssetRepository.findByStorageKey(any())).thenReturn(Optional.empty());
        when(staticAssetRepository.saveAndFlush(any(StaticAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssetResponse response = staticAssetService.upload(png("Pixel.PNG"));

        assertThat(response.url()).startsWith(URL_PREFIX + "image/");
        assertThat(response.mimeType()).isEqualTo("image/png");
        assertThat(response.originalName()).isEqualTo("Pixel.PNG");
        assertThat(response.size()).isEqualTo(PNG_BYTES.length);
        assertThat(response.hash()).hasSize(64);
        assertThat(response.kind()).isEqualTo("IMAGE");
        assertThat(response.status()).isEqualTo("ACTIVE");

        String storageKey = response.url().substring(URL_PREFIX.length());
        assertThat(storageKey).endsWith(".png");
        assertThat(Files.exists(tempDir.resolve(storageKey))).isTrue();
        assertThat(Files.readAllBytes(tempDir.resolve(storageKey))).isEqualTo(PNG_BYTES);
    }

    @Test
    void sameContentIsDeduplicatedByStorageKey() {
        StaticAsset existing = asset(9L, "image/aa/hash.png", StaticAsset.AssetStatus.ACTIVE);
        when(staticAssetRepository.findByStorageKey(any())).thenReturn(Optional.of(existing));

        AssetResponse response = staticAssetService.upload(png("again.png"));

        assertThat(response.id()).isEqualTo(9L);
        verify(staticAssetRepository, never()).saveAndFlush(any());
    }

    @Test
    void reactivatesLogicallyDeletedAssetWithSameContent() throws IOException {
        StaticAsset deleted = asset(10L, "image/bb/hash.png", StaticAsset.AssetStatus.DELETED);
        when(staticAssetRepository.findByStorageKey(any())).thenReturn(Optional.of(deleted));
        when(staticAssetRepository.saveAndFlush(any(StaticAsset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssetResponse response = staticAssetService.upload(png("back.png"));

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(deleted.getStatus()).isEqualTo(StaticAsset.AssetStatus.ACTIVE);
        verify(staticAssetRepository).saveAndFlush(deleted);
    }

    @Test
    void rejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "script.exe", "application/octet-stream", PNG_BYTES);

        assertTypeRejected(file);
        verify(staticAssetRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsMimeTypeThatDoesNotMatchExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/jpeg", PNG_BYTES);

        assertTypeRejected(file);
    }

    @Test
    void rejectsFileContentThatIsNotARealImage() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
                "<?php system($_GET['cmd']); ?>".getBytes());

        assertTypeRejected(file);
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        byte[] big = new byte[(int) DataSize.ofKilobytes(1).toBytes() + 1];
        System.arraycopy(PNG_BYTES, 0, big, 0, PNG_BYTES.length);
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", big);

        assertThatThrownBy(() -> staticAssetService.upload(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ASSET_TOO_LARGE));
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> staticAssetService.upload(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void softDeleteOnlyMarksStatusAndKeepsFile() throws IOException {
        StaticAsset asset = asset(1L, "image/cc/keep.png", StaticAsset.AssetStatus.ACTIVE);
        storageService.store(asset.getStorageKey(), PNG_BYTES);
        when(staticAssetRepository.findById(1L)).thenReturn(Optional.of(asset));

        staticAssetService.delete(1L, false);

        assertThat(asset.getStatus()).isEqualTo(StaticAsset.AssetStatus.DELETED);
        verify(staticAssetRepository).saveAndFlush(asset);
        verify(staticAssetRepository, never()).delete(any());
        assertThat(Files.exists(tempDir.resolve(asset.getStorageKey()))).isTrue();
    }

    @Test
    void hardDeleteRemovesRecordAndFile() throws IOException {
        StaticAsset asset = asset(2L, "image/dd/remove.png", StaticAsset.AssetStatus.ACTIVE);
        storageService.store(asset.getStorageKey(), PNG_BYTES);
        when(staticAssetRepository.findById(2L)).thenReturn(Optional.of(asset));

        staticAssetService.delete(2L, true);

        verify(staticAssetRepository).delete(asset);
        assertThat(Files.exists(tempDir.resolve(asset.getStorageKey()))).isFalse();
    }

    @Test
    void deletingUnknownAssetReturnsNotFound() {
        when(staticAssetRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staticAssetService.delete(404L, false))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ASSET_NOT_FOUND));
    }

    @Test
    void listFiltersByKindAndStatusWithNewestFirst() {
        AssetQuery query = new AssetQuery();
        query.setKind("IMAGE");
        query.setStatus("ACTIVE");
        when(staticAssetRepository.findByKindAndStatus(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(asset(1L, "image/ee/a.png", StaticAsset.AssetStatus.ACTIVE))));

        var response = staticAssetService.list(query);

        assertThat(response.items()).hasSize(1);
        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(staticAssetRepository).findByKindAndStatus(any(), any(), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    private void assertTypeRejected(MockMultipartFile file) {
        assertThatThrownBy(() -> staticAssetService.upload(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ASSET_TYPE_NOT_ALLOWED));
    }

    private MockMultipartFile png(String fileName) {
        return new MockMultipartFile("file", fileName, "image/png", PNG_BYTES);
    }

    private StaticAsset asset(Long id, String storageKey, StaticAsset.AssetStatus status) {
        StaticAsset asset = new StaticAsset();
        asset.setId(id);
        asset.setStorageKey(storageKey);
        asset.setUrl(URL_PREFIX + storageKey);
        asset.setOriginalName("pixel.png");
        asset.setMimeType("image/png");
        asset.setFileSize((long) PNG_BYTES.length);
        asset.setFileHash("f".repeat(64));
        asset.setKind(StaticAsset.AssetKind.IMAGE);
        asset.setStatus(status);
        return asset;
    }
}
