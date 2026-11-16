package com.example.hrsblog.security;

import com.example.hrsblog.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 请求体大小闸门：挡住匿名写接口的超大请求体。
 *
 * <p>拦截器在<b>参数解析之前</b>执行，因此超大的 JSON 还没被反序列化进内存就被挡掉，
 * 避免公开的匿名写接口被超大 body 打满内存。响应体沿用统一信封
 * {@code {code, message, data}}。
 *
 * <p>只按 {@code Content-Length} 判断：分块传输（chunked）拿不到长度时放行，
 * 由 DTO 上的字段长度上限兜底。
 */
public class RequestBodySizeInterceptor implements HandlerInterceptor {

    private final long maxBytes;

    public RequestBodySizeInterceptor(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long contentLength = request.getContentLengthLong();
        if (contentLength <= maxBytes) {
            return true;
        }
        SecurityJsonWriter.write(response, ErrorCode.REQUEST_BODY_TOO_LARGE, ErrorCode.REQUEST_BODY_TOO_LARGE.message());
        return false;
    }
}
