package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.dto.LoginRequest;
import com.example.hrsblog.dto.LoginResponse;
import com.example.hrsblog.dto.PasswordChangeRequest;
import com.example.hrsblog.dto.PasswordChangeResponse;
import com.example.hrsblog.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * 管理员认证接口。
 *
 * <ul>
 *   <li>{@code POST /login}：匿名登录，成功后返回 JWT，失败统一 401；</li>
 *   <li>{@code PUT /password}：需 ROLE_ADMIN，校验原密码后更新 BCrypt 哈希并签发新 JWT。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PutMapping("/password")
    public ApiResponse<PasswordChangeResponse> changePassword(Principal principal,
                                                             @Valid @RequestBody PasswordChangeRequest request) {
        return ApiResponse.ok(authService.changePassword(principal.getName(), request));
    }
}
