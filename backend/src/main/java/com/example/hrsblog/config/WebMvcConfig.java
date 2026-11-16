package com.example.hrsblog.config;

import com.example.hrsblog.security.RequestBodySizeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
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

    /**
     * 匿名写接口的请求体上限：友链申请的合法报文（名称 100 + 地址 500×2 + 头像 500
     * + 简介 500 + 联系方式 200）连同 JSON 开销仍远小于 16KB。
     */
    private static final long MAX_ANONYMOUS_BODY_BYTES = 16 * 1024;

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

    /**
     * 公开的匿名写接口（友链申请）必须挡住超大请求体：
     * 拦截器在参数解析之前执行，超大的 JSON 不会被反序列化进内存。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequestBodySizeInterceptor(MAX_ANONYMOUS_BODY_BYTES))
                .addPathPatterns("/api/visitors/friend-links");
    }
}
