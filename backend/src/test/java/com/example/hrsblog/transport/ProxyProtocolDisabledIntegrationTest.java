package com.example.hrsblog.transport;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jetty.JettyWebServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 默认（未配置 {@code hrsblog.transport.proxy-protocol-version}）时不应该注册任何 PROXY 连接工厂，
 * 容器行为与引入该特性之前完全一致（零回归）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProxyProtocolDisabledIntegrationTest {

    @Autowired
    private ServletWebServerApplicationContext applicationContext;

    @Test
    void noProxyConnectionFactoryIsRegisteredByDefault() {
        assertThat(applicationContext.getWebServer()).isInstanceOf(JettyWebServer.class);

        Server server = ((JettyWebServer) applicationContext.getWebServer()).getServer();
        assertThat(server.getConnectors()).isNotEmpty();
        for (Connector connector : server.getConnectors()) {
            assertThat(connector.getProtocols())
                    .as("未启用 PROXY protocol 时不应出现 proxy 协议工厂")
                    .noneMatch(protocol -> protocol.contains("proxy"));
        }
    }
}
