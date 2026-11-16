package com.example.hrsblog.transport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROXY protocol 解析的集成测试：直接用原始 Socket 与内嵌 Jetty 通信。
 *
 * <p>MockMvc 不经过真正的 TCP 连接，无法覆盖「连接首字节探测」这一关键行为，
 * 因此这里用 {@link Socket} 手写请求字节。
 *
 * <p>覆盖三个场景：
 * <ol>
 *   <li>携带 PROXY v2（二进制）头 —— {@code getRemoteAddr()} 应返回头里携带的真实客户端 IP；</li>
 *   <li>携带 PROXY v1（文本）头 —— 同样应返回真实客户端 IP；</li>
 *   <li>不携带 PROXY 头的普通 HTTP —— 探测失败后回退到 HTTP 连接工厂，
 *       行为与未启用 PROXY protocol 时一致（deploy.sh 的健康检查走的就是这条路径）。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "hrsblog.transport.proxy-protocol-version=v2")
@ActiveProfiles("test")
class ProxyProtocolIntegrationTest {

    private static final String ECHO_PATH = "/api/visitors/transport/echo";

    @LocalServerPort
    private int port;

    @Test
    void proxyV2HeaderExposesRealClientAddress() throws IOException {
        byte[] header = proxyV2Header("203.0.113.7", 12345, "127.0.0.1", port);

        String body = exchange(header);

        assertThat(body).contains("203.0.113.7:12345");
    }

    @Test
    void proxyV1HeaderExposesRealClientAddress() throws IOException {
        byte[] header = ("PROXY TCP4 198.51.100.9 127.0.0.1 54321 " + port + "\r\n")
                .getBytes(StandardCharsets.US_ASCII);

        String body = exchange(header);

        assertThat(body).contains("198.51.100.9:54321");
    }

    @Test
    void plainHttpRequestFallsBackToDirectConnection() throws IOException {
        String body = exchange(new byte[0]);

        assertThat(body).contains("127.0.0.1:");
        assertThat(body).doesNotContain("203.0.113.7");
    }

    @Test
    void malformedProxyHeaderClosesConnectionWithoutBreakingTheServer() throws IOException {
        // 签名正确但版本号非法（0x31 = 版本 3）：Jetty 判定为非法头并直接关闭该连接，
        // 不返回 5xx，也不影响后续连接。
        byte[] malformed = proxyV2Header("203.0.113.7", 12345, "127.0.0.1", port);
        malformed[12] = 0x31;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(15_000);
            socket.getOutputStream().write(concat(malformed, httpRequest()));
            socket.getOutputStream().flush();

            assertThat(readAll(socket.getInputStream()))
                    .as("非法 PROXY 头不应产生 HTTP 响应")
                    .isEmpty();
        }

        // 服务器仍然存活：普通请求照常处理
        assertThat(exchange(new byte[0])).contains("127.0.0.1:");
    }

    /**
     * 把可选的 PROXY 头与一个普通 HTTP 请求拼接后发给容器，返回响应体。
     */
    private String exchange(byte[] proxyHeader) throws IOException {
        byte[] request = concat(proxyHeader, httpRequest());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 5_000);
            socket.setSoTimeout(15_000);

            OutputStream out = socket.getOutputStream();
            out.write(request);
            out.flush();

            String response = readAll(socket.getInputStream());
            int bodyStart = response.indexOf("\r\n\r\n");
            assertThat(bodyStart).as("响应应包含完整的 HTTP 头，实际为: %s", response).isGreaterThanOrEqualTo(0);
            return response.substring(bodyStart + 4);
        }
    }

    private byte[] httpRequest() {
        return ("GET " + ECHO_PATH + " HTTP/1.1\r\n"
                + "Host: 127.0.0.1:" + port + "\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * 构造 PROXY protocol v2 二进制头（AF_INET + STREAM）。
     *
     * <pre>
     * struct proxy_hdr_v2 {
     *     uint8_t  sig[12];  // 0D 0A 0D 0A 00 0D 0A 51 55 49 54 0A
     *     uint8_t  ver_cmd;  // 0x21 = 版本 2 + PROXY 命令
     *     uint8_t  fam;      // 0x11 = AF_INET + STREAM
     *     uint16_t len;      // 后续地址块长度，这里是 12
     * };
     * </pre>
     */
    private static byte[] proxyV2Header(String sourceIp, int sourcePort, String destinationIp, int destinationPort) {
        byte[] header = new byte[28];
        byte[] signature = {0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A};
        System.arraycopy(signature, 0, header, 0, signature.length);
        header[12] = 0x21;
        header[13] = 0x11;
        header[14] = 0x00;
        header[15] = 0x0C;
        System.arraycopy(ipv4(sourceIp), 0, header, 16, 4);
        System.arraycopy(ipv4(destinationIp), 0, header, 20, 4);
        header[24] = (byte) (sourcePort >>> 8);
        header[25] = (byte) sourcePort;
        header[26] = (byte) (destinationPort >>> 8);
        header[27] = (byte) destinationPort;
        return header;
    }

    private static byte[] ipv4(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("非法 IPv4 地址: " + ip);
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i]);
        }
        return bytes;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
