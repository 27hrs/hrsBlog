package com.example.hrsblog.security;

import com.example.hrsblog.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/** 未认证（无 token / token 无效或过期 / 账号禁用）统一 401 JSON。 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        String message = reason instanceof String text && StringUtils.hasText(text)
                ? text
                : "未认证或登录状态已失效";
        SecurityJsonWriter.write(response, ErrorCode.UNAUTHORIZED, message);
    }
}
