package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.config.JwtProperties;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.dto.LoginRequest;
import com.example.hrsblog.dto.LoginResponse;
import com.example.hrsblog.dto.PasswordChangeRequest;
import com.example.hrsblog.dto.PasswordChangeResponse;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 登录流程单元测试：成功、密码错、账号不存在、账号禁用，以及 last_login_at 更新。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Password123";

    @Mock
    private AdminUserRepository adminUserRepository;

    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
                "auth-service-test-secret-0123456789-abcdefghij", "hrsblog-test", Duration.ofHours(2)));
        authService = new AuthService(adminUserRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    void loginSucceedsAndUpdatesLastLoginAt() {
        AdminUser admin = admin(3L, "hrs-admin", AdminUser.AdminStatus.ACTIVE);
        when(adminUserRepository.findByUsername("hrs-admin")).thenReturn(Optional.of(admin));

        LoginResponse response = authService.login(new LoginRequest("  hrs-admin  ", PASSWORD));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(Duration.ofHours(2).toSeconds());
        assertThat(response.user().id()).isEqualTo(3L);
        assertThat(response.user().username()).isEqualTo("hrs-admin");
        assertThat(response.user().displayName()).isEqualTo("Hrs Admin");
        assertThat(admin.getLastLoginAt()).isNotNull();
        assertThat(jwtTokenProvider.parse(response.accessToken()).username()).isEqualTo("hrs-admin");
        verify(adminUserRepository).save(admin);
    }

    @Test
    void rejectsWrongPasswordWithoutLeakingReason() {
        when(adminUserRepository.findByUsername("hrs-admin"))
                .thenReturn(Optional.of(admin(3L, "hrs-admin", AdminUser.AdminStatus.ACTIVE)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("hrs-admin", "WrongPassword1")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                    assertThat(ex.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.message());
                });
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void rejectsUnknownAccountWithSameError() {
        when(adminUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void rejectsDisabledAccountEvenWithCorrectPassword() {
        AdminUser disabled = admin(4L, "disabled-admin", AdminUser.AdminStatus.DISABLED);
        when(adminUserRepository.findByUsername("disabled-admin")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.login(new LoginRequest("disabled-admin", PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
                    assertThat(ex.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.message());
                });
        assertThat(disabled.getLastLoginAt()).isNull();
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void loginFailureMessagesAreIdenticalForDifferentCauses() {
        String unknownUserMessage = messageOf(() -> {
            when(adminUserRepository.findByUsername("nobody")).thenReturn(Optional.empty());
            authService.login(new LoginRequest("nobody", PASSWORD));
        });
        String wrongPasswordMessage = messageOf(() -> {
            when(adminUserRepository.findByUsername("hrs-admin"))
                    .thenReturn(Optional.of(admin(5L, "hrs-admin", AdminUser.AdminStatus.ACTIVE)));
            authService.login(new LoginRequest("hrs-admin", "AnotherPass1"));
        });

        assertThat(unknownUserMessage).isEqualTo(wrongPasswordMessage);
    }

    @Test
    void changePasswordEncodesNewHashAndIssuesNewToken() {
        AdminUser admin = admin(6L, "pwd-admin", AdminUser.AdminStatus.ACTIVE);
        when(adminUserRepository.findByUsername("pwd-admin")).thenReturn(Optional.of(admin));

        PasswordChangeResponse response = authService.changePassword(
                "pwd-admin", new PasswordChangeRequest(PASSWORD, "BrandNewPass456"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(Duration.ofHours(2).toSeconds());
        assertThat(jwtTokenProvider.parse(response.accessToken()).username()).isEqualTo("pwd-admin");
        assertThat(passwordEncoder.matches("BrandNewPass456", admin.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isFalse();
        assertThat(admin.getLastLoginAt()).isNull();
        verify(adminUserRepository).save(admin);
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        AdminUser admin = admin(7L, "pwd-admin", AdminUser.AdminStatus.ACTIVE);
        when(adminUserRepository.findByUsername("pwd-admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.changePassword(
                "pwd-admin", new PasswordChangeRequest("WrongOldPass1", "BrandNewPass456")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED_PASSWORD);
                    assertThat(ex.getMessage()).isEqualTo(ErrorCode.UNAUTHORIZED_PASSWORD.message());
                });
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsNewPasswordEqualToOldOne() {
        AdminUser admin = admin(8L, "pwd-admin", AdminUser.AdminStatus.ACTIVE);
        when(adminUserRepository.findByUsername("pwd-admin")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authService.changePassword(
                "pwd-admin", new PasswordChangeRequest(PASSWORD, PASSWORD)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void changePasswordRejectsUnknownAccount() {
        when(adminUserRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(
                "ghost", new PasswordChangeRequest(PASSWORD, "BrandNewPass456")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    private String messageOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("预期抛出 BusinessException");
        } catch (BusinessException ex) {
            return ex.getMessage();
        }
    }

    private AdminUser admin(Long id, String username, AdminUser.AdminStatus status) {
        AdminUser admin = new AdminUser();
        admin.setId(id);
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(PASSWORD));
        admin.setDisplayName("Hrs Admin");
        admin.setStatus(status);
        return admin;
    }
}
