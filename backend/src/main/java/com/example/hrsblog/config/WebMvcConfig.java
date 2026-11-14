package com.example.hrsblog.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 静态资源对外映射：{@code GET /assets/**} 直接由 Spring 从本地存储目录读取。
 *
 * <p>生产可通过 nginx 直接把同一目录挂在 {@code /assets/} 下以绕过应用进程；
 * 两条路径的 URL 结构保持一致，因此数据库里保存的链接无需变化。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(storageProperties.localDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("无法创建静态资源目录: " + root, ex);
        }
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}
