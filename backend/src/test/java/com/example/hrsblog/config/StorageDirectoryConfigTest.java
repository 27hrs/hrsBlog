package com.example.hrsblog.config;

import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.StaticAsset;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.repository.StaticAssetRepository;
import com.example.hrsblog.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 存储目录配置切换测试。
 *
 * <p>覆盖计划中的「修改 storage.local-dir 后文件写入新目录」：
 * 通过 {@code @TestPropertySource} 覆盖 local-dir 与 public-base-url，
 * 断言文件落到新目录、数据库里的链接前缀随之变化、{@code /assets/**} 也从新目录读取。
 *
 * <p>使用独立的 H2 库，避免与其它上下文共享内存库时相互影响。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hrsblog-storage-config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "hrsblog.storage.local-dir=target/test-storage-alt/assets",
        "hrsblog.storage.public-base-url=https://alt-cdn.test"
})
class StorageDirectoryConfigTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final String ALT_BASE_URL = "https://alt-cdn.test";
    private static final String CONFIGURED_DIR = "target/test-storage-alt/assets";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StorageProperties storageProperties;

    @Autowired
    private StaticAssetRepository staticAssetRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void filesFollowConfiguredStorageDirectory() throws Exception {
        assertThat(storageProperties.localDir()).isEqualTo(CONFIGURED_DIR);
        assertThat(storageProperties.publicBaseUrl()).isEqualTo(ALT_BASE_URL);

        AdminUser admin = admin("storage-config-admin");
        String token = "Bearer " + jwtTokenProvider.issue(admin).token();

        mockMvc.perform(multipart("/api/admin/assets")
                        .file(new MockMultipartFile("file", "pixel.png", "image/png", PNG))
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.url")
                        .value(org.hamcrest.Matchers.startsWith(ALT_BASE_URL + "/assets/image/")));

        StaticAsset asset = staticAssetRepository.findAll().getFirst();
        Path configuredRoot = Path.of(CONFIGURED_DIR).toAbsolutePath().normalize();
        assertThat(configuredRoot.resolve(asset.getStorageKey())).hasBinaryContent(PNG);

        // 静态资源映射同样跟随配置：从新目录读取，且 URL 前缀来自 public-base-url
        String publicPath = asset.getUrl().substring(ALT_BASE_URL.length());
        mockMvc.perform(get(publicPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        assertThat(Files.exists(configuredRoot.resolve(asset.getStorageKey()))).isTrue();
    }

    private AdminUser admin(String username) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode("Password123"));
        admin.setDisplayName("Storage Config Admin");
        admin.setStatus(AdminUser.AdminStatus.ACTIVE);
        return adminUserRepository.saveAndFlush(admin);
    }
}
