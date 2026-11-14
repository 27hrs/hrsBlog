package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 本地存储实现测试：链接拼接、写读删，以及路径穿越防护。 */
class LocalStorageServiceTest {

    private static final byte[] CONTENT = "asset-content".getBytes();

    @TempDir
    Path tempDir;

    @Test
    void storesReadsAndDeletesFile() {
        LocalStorageService storage = storage("https://blog.example.com");

        storage.store("image/ab/pixel.png", CONTENT);

        assertThat(storage.exists("image/ab/pixel.png")).isTrue();
        assertThat(tempDir.resolve("image/ab/pixel.png")).hasBinaryContent(CONTENT);
        assertThat(storage.urlOf("image/ab/pixel.png"))
                .isEqualTo("https://blog.example.com/assets/image/ab/pixel.png");

        storage.delete("image/ab/pixel.png");
        assertThat(storage.exists("image/ab/pixel.png")).isFalse();
    }

    @Test
    void urlIsRelativeWhenBaseUrlIsBlank() {
        LocalStorageService storage = storage("");

        assertThat(storage.urlOf("image/cd/pixel.webp")).isEqualTo("/assets/image/cd/pixel.webp");
    }

    @Test
    void baseUrlTrailingSlashIsNormalized() {
        LocalStorageService storage = storage("https://cdn.example.com/");

        assertThat(storage.urlOf("image/ef/pixel.gif")).isEqualTo("https://cdn.example.com/assets/image/ef/pixel.gif");
    }

    @Test
    void rejectsPathTraversalOnWrite() {
        LocalStorageService storage = storage("");

        assertThatThrownBy(() -> storage.store("../escape.png", CONTENT))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
        assertThat(Files.exists(tempDir.getParent().resolve("escape.png"))).isFalse();
    }

    @Test
    void rejectsBlankStorageKey() {
        LocalStorageService storage = storage("");

        assertThatThrownBy(() -> storage.exists(" "))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void deleteIsIdempotent() {
        LocalStorageService storage = storage("");

        storage.delete("image/00/missing.png");

        assertThat(storage.exists("image/00/missing.png")).isFalse();
    }

    private LocalStorageService storage(String publicBaseUrl) {
        return new LocalStorageService(new StorageProperties(
                tempDir.toString(), publicBaseUrl, DataSize.ofMegabytes(5), List.of("png", "jpg", "jpeg", "webp", "gif")));
    }
}
