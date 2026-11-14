package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.dto.LoginRequest;
import com.example.hrsblog.dto.LoginResponse;
import com.example.hrsblog.dto.PasswordChangeRequest;
import com.example.hrsblog.dto.PasswordChangeResponse;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 管理员登录流程：
 * 用户名查询 → BCrypt 校验 → 状态检查（DISABLED 拒绝）→ 签发 JWT → 更新 last_login_at。
 *
 * <p>安全要点：
 * <ul>
 *   <li>「账号不存在 / 密码错误 / 账号禁用」统一返回 {@link ErrorCode#INVALID_CREDENTIALS}，防账号枚举；</li>
 *   <li>账号不存在时仍然执行一次 BCrypt 校验，抹平响应时间差异，避免通过耗时判断账号是否存在。</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    /** 账号不存在时用来消耗同等 BCrypt 时间的占位哈希（启动时随机生成，不含任何真实口令）。 */
    private final String timingEqualizerHash;

    public AuthService(AdminUserRepository adminUserRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.timingEqualizerHash = passwordEncoder.encode("hrsblog-login-timing-equalizer");
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        AdminUser admin = adminUserRepository.findByUsername(username).orElse(null);
        if (admin == null) {
            passwordEncoder.matches(request.password(), timingEqualizerHash);
            log.warn("管理员登录失败：账号不存在 username={}", username);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            log.warn("管理员登录失败：密码错误 username={}", username);
            throw invalidCredentials();
        }
        if (admin.getStatus() != AdminUser.AdminStatus.ACTIVE) {
            log.warn("管理员登录失败：账号已被禁用 username={}", username);
            throw invalidCredentials();
        }

        admin.setLastLoginAt(LocalDateTime.now());
        adminUserRepository.save(admin);

        JwtTokenProvider.IssuedToken issued = jwtTokenProvider.issue(admin);
        log.info("管理员登录成功 username={}", username);
        return new LoginResponse(
                issued.token(),
                TOKEN_TYPE,
                issued.expiresInSeconds(),
                new LoginResponse.AdminUserSummary(
                        admin.getId(),
                        admin.getUsername(),
                        admin.getDisplayName(),
                        admin.getAvatarUrl()));
    }

    /**
     * 修改当前登录管理员的密码：
     * 旧密码 BCrypt 校验 → 新密码不与旧密码相同 → 重新 BCrypt 编码落库 → 签发新 JWT。
     *
     * <p>旧 Token 不主动吊销（无状态），在有效期内仍可用；前端应以新 Token 覆盖本地存储。
     *
     * @param username 从 SecurityContext 取得的当前登录账号
     */
    @Transactional
    public PasswordChangeResponse changePassword(String username, PasswordChangeRequest request) {
        AdminUser admin = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        if (!passwordEncoder.matches(request.oldPassword(), admin.getPasswordHash())) {
            log.warn("修改密码失败：原密码错误 username={}", username);
            throw new BusinessException(ErrorCode.UNAUTHORIZED_PASSWORD);
        }
        if (passwordEncoder.matches(request.newPassword(), admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "新密码不能与原密码相同");
        }

        admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        adminUserRepository.save(admin);

        JwtTokenProvider.IssuedToken issued = jwtTokenProvider.issue(admin);
        log.info("管理员修改密码成功 username={}", username);
        return new PasswordChangeResponse(issued.token(), TOKEN_TYPE, issued.expiresInSeconds());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
}