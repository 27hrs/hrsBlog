package com.example.hrsblog.security;

import com.example.hrsblog.common.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 安全过滤器链内无法交给 {@code @RestControllerAdvice} 处理，这里手写统一错误体。
 *
 * <p>只输出固定文案（内部常量），不反射外部输入，避免响应体注入。
 */
final class SecurityJsonWriter {

    private SecurityJsonWriter() {
    }

    static void write(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"code\":" + errorCode.code()
                + ",\"message\":\"" + escape(message) + "\""
                + ",\"data\":null}";
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
