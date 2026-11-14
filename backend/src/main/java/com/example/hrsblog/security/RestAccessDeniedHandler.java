package com.example.hrsblog.security;

import com.example.hrsblog.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 已认证但权限不足（例如普通用户访问管理员接口）统一 403 JSON。 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityJsonWriter.write(response, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.message());
    }
}
