package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.dto.LoginRequest;
import com.example.hrsblog.dto.LoginResponse;
import com.example.hrsblog.dto.PasswordChangeRequest;
import com.example.hrsblog.dto.PasswordChangeResponse;
import com.example.hrsblog.dto.UsernameChangeRequest;
import com.example.hrsblog.dto.UsernameChangeResponse;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
 *
 * <p>本类同时负责账号维护（修改密码、修改用户名）：两者都会重新签发 JWT，
 * 因为令牌的 subject 就是用户名，凭据变更后旧令牌不再代表当前身份。
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

    /**
     * 修改当前登录管理员的用户名：
     * 唯一性预检（排除自身）→ 更新用户名 → 重新签发 JWT。
     *
     * <p>JWT 的 subject 就是用户名，改名后旧 Token 无法再定位到账号，
     * 因此这里必须返回新 Token，前端应据此覆盖本地令牌。
     *
     * @param currentUsername 从 SecurityContext 取得的当前登录账号（JWT subject）
     * @param request         新用户名请求
     * @return 新令牌与更新后的管理员资料
     */
    @Transactional
    public UsernameChangeResponse changeUsername(String currentUsername, UsernameChangeRequest request) {
        String newUsername = request.newUsername().trim();
        AdminUser admin = adminUserRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录"));

        // 排除自身：重复提交同一个用户名视为「保持不变」，只重新签发 Token
        if (adminUserRepository.existsByUsernameAndIdNot(newUsername, admin.getId())) {
            log.warn("修改用户名失败：用户名已被其他账号占用 username={}", newUsername);
            throw new BusinessException(ErrorCode.USERNAME_CONFLICT);
        }

        if (!newUsername.equals(admin.getUsername())) {
            admin.setUsername(newUsername);
            try {
                adminUserRepository.saveAndFlush(admin);
            } catch (DataIntegrityViolationException ex) {
                // 并发改名撞上同一用户名时由数据库唯一索引兜底
                log.warn("修改用户名失败：用户名唯一约束冲突 username={}", newUsername);
                throw new BusinessException(ErrorCode.USERNAME_CONFLICT, ErrorCode.USERNAME_CONFLICT.message(), ex);
            }
            log.info("管理员修改用户名成功 oldUsername={} newUsername={}", currentUsername, newUsername);
        } else {
            log.info("管理员提交相同用户名，保持不变 username={}", newUsername);
        }

        JwtTokenProvider.IssuedToken issued = jwtTokenProvider.issue(admin);
        return new UsernameChangeResponse(
                issued.token(),
                TOKEN_TYPE,
                issued.expiresInSeconds(),
                new LoginResponse.AdminUserSummary(
                        admin.getId(),
                        admin.getUsername(),
                        admin.getDisplayName(),
                        admin.getAvatarUrl()));
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
}