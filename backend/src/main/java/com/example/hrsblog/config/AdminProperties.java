package com.example.hrsblog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 初始管理员引导配置（可选）。
 *
 * <p>只有当账号密码都通过环境变量提供、且 {@code admin_user} 表为空时才会创建一条管理员记录，
 * 用来解决「全新数据库没有账号可以登录」的问题；创建后请立即登录修改密码。
 */
@ConfigurationProperties(prefix = "hrsblog.admin")
public record AdminProperties(
        String bootstrapUsername,
        String bootstrapPassword,
        String bootstrapDisplayName
) {
}
