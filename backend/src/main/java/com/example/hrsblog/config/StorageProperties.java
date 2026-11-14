package com.example.hrsblog.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 静态资源存储配置（全部可在 application.yaml 中调整，生产用环境变量覆盖）。
 *
 * @param localDir      本地存储根目录，应用启动时自动创建，失败即快速报错
 * @param publicBaseUrl 写入数据库的链接前缀，留空则返回站内相对路径 {@code /assets/...}
 * @param maxSize       单文件大小上限（如 5MB）
 * @param allowedTypes  允许的扩展名白名单（小写，不带点）
 */
@Validated
@ConfigurationProperties(prefix = "hrsblog.storage")
public record StorageProperties(
        @NotBlank(message = "hrsblog.storage.local-dir 不能为空")
        String localDir,

        String publicBaseUrl,

        @NotNull(message = "hrsblog.storage.max-size 不能为空")
        DataSize maxSize,

        @NotEmpty(message = "hrsblog.storage.allowed-types 不能为空")
        List<String> allowedTypes
) {

    public StorageProperties {
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
    }
}
