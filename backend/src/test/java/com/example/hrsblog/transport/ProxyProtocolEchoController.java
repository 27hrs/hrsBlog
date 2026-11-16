package com.example.hrsblog.transport;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试专用的客户端地址回显接口：返回 Servlet 层看到的 {@code getRemoteAddr()} / {@code getRemotePort()}。
 *
 * <p>挂在 {@code /api/visitors/**} 下是为了复用既有安全配置的匿名放行规则，
 * 避免为了测试去改动生产代码里的授权策略。
 */
@RestController
public class ProxyProtocolEchoController {

    @GetMapping(value = "/api/visitors/transport/echo", produces = MediaType.TEXT_PLAIN_VALUE)
    public String echo(HttpServletRequest request) {
        return request.getRemoteAddr() + ":" + request.getRemotePort();
    }
}
