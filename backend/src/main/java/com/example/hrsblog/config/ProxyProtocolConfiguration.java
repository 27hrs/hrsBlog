package com.example.hrsblog.config;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ProxyConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jetty.JettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * PROXY protocol 支持（frp 的 {@code transport.proxyProtocolVersion}）。
 *
 * <p>启用后，内嵌 Jetty 会在 HTTP 连接工厂前面插入 {@link ProxyConnectionFactory}：
 * 它按 TCP 流首字节自动探测——首字节匹配 v2 签名（{@code 0D 0A 0D 0A 00 0D 0A 51 55 49 54 0A}）
 * 或 v1 文本签名（{@code "PROXY "}）时按 PROXY protocol 解析出真实客户端 IP/端口，
 * 之后 {@code request.getRemoteAddr()} / {@code getRemotePort()} 返回的就是真实客户端地址；
 * 未携带 PROXY 头的连接（例如 deploy.sh 的 {@code curl http://127.0.0.1:27000/} 健康检查、
 * 本地直连开发）会原样回退到 HTTP 连接工厂，不受影响。
 *
 * <p>配置项 {@code hrsblog.transport.proxy-protocol-version} 为空时本配置整体不生效，
 * 行为与引入该特性之前完全一致。
 *
 * <p><b>安全提示</b>：任何能直连该端口的客户端都能伪造 PROXY 头冒充来源 IP，
 * 因此启用后必须确保服务端口只在内网 / frp 反向代理后面暴露，不能直接对公网开放。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ProxyConnectionFactory.class, JettyServerCustomizer.class})
public class ProxyProtocolConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ProxyProtocolConfiguration.class);

    /**
     * 仅在 {@code hrsblog.transport.proxy-protocol-version} 非空时注册连接工厂；
     * 未配置（默认）时不注册任何工厂，容器行为与现状一致。
     */
    @Bean
    @Conditional(ProxyProtocolEnabledCondition.class)
    public JettyServerCustomizer proxyProtocolJettyCustomizer(TransportProperties transportProperties) {
        return (Server server) -> customize(server, transportProperties);
    }

    private void customize(Server server, TransportProperties transportProperties) {
        for (Connector connector : server.getConnectors()) {
            if (!(connector instanceof ServerConnector serverConnector)) {
                continue;
            }
            // 必须插到最前面：连接工厂列表的第一个即为默认协议，
            // 这样每条新连接都先经过探测器，识别不出 PROXY 头时再回退到 HTTP。
            serverConnector.addFirstConnectionFactory(new ProxyConnectionFactory());
            log.info("已启用 PROXY protocol 解析（配置值 {}），真实客户端 IP 会透传到 request.getRemoteAddr()",
                    transportProperties.proxyProtocolVersion());
        }
    }

    /**
     * 判断 {@code hrsblog.transport.proxy-protocol-version} 是否配置了非空值。
     */
    static class ProxyProtocolEnabledCondition implements Condition {

        private static final String PROPERTY = "hrsblog.transport.proxy-protocol-version";

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String version = context.getEnvironment().getProperty(PROPERTY);
            return version != null && !version.isBlank();
        }
    }
}
