package com.example.hrsblog.config;

import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 初始管理员引导（可选、幂等）。
 *
 * <p>仅当同时满足以下条件时才创建账号：环境变量提供了 {@code ADMIN_BOOTSTRAP_USERNAME/PASSWORD}，
 * 且 {@code admin_user} 表为空。用于解决全新数据库没有账号可登录的问题；
 * 密码使用与登录一致的 BCrypt 编码器写入，不会在容器里留下明文配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = adminProperties.bootstrapUsername();
        String password = adminProperties.bootstrapPassword();
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return;
        }
        if (adminUserRepository.count() > 0) {
            log.info("admin_user 表已存在管理员账号，跳过初始账号创建");
            return;
        }
        username = username.trim();
        if (username.length() < 3 || username.length() > 64) {
            log.error("ADMIN_BOOTSTRAP_USERNAME 长度必须在 3-64 之间，已跳过初始账号创建");
            return;
        }
        if (password.length() < 8 || password.length() > 64) {
            log.error("ADMIN_BOOTSTRAP_PASSWORD 长度必须在 8-64 之间，已跳过初始账号创建");
            return;
        }

        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        String displayName = StringUtils.hasText(adminProperties.bootstrapDisplayName())
                ? adminProperties.bootstrapDisplayName().trim()
                : username;
        admin.setDisplayName(displayName);
        admin.setStatus(AdminUser.AdminStatus.ACTIVE);
        adminUserRepository.saveAndFlush(admin);
        log.warn("已根据环境变量创建初始管理员 '{}'，请登录后立即修改密码，并从部署环境中删除 ADMIN_BOOTSTRAP_* 变量", username);
    }
}
