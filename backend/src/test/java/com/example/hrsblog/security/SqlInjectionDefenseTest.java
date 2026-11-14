package com.example.hrsblog.security;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.domain.Tag;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.repository.ArticleRepository;
import com.example.hrsblog.repository.StaticAssetRepository;
import com.example.hrsblog.repository.TagRepository;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SQL 注入专项测试。
 *
 * <p>防御策略是「双层」的：
 * <ol>
 *   <li>入参格式校验（slug / tag 只允许安全字符），非法 payload 在进入查询前就被 400 拒绝；</li>
 *   <li>所有查询都是 Spring Data 派生方法或参数化查询，不存在字符串拼接，因此即便内容型字段
 *       （如标题）携带 payload，也只会作为数据被字面保存与返回。</li>
 * </ol>
 * 断言重点：数据表结构与数据完整性不受影响，payload 不会改变查询语义。
 */
class SqlInjectionDefenseTest extends AbstractIntegrationTest {

    private static final List<String> PAYLOADS = List.of(
            "' OR 1=1 --",
            "%; DROP TABLE article; --",
            "admin'--",
            "1' UNION SELECT id, username, password_hash FROM admin_user --");

    private static final String ARTICLES_URL = "/api/visitors/articles";

    @Test
    void loginPayloadsAreRejectedAndAccountsRemainIntact() throws Exception {
        createAdmin("sqli-admin");
        long adminsBefore = adminUserRepository.count();

        for (String payload : PAYLOADS) {
            mockMvc.perform(post("/api/admin/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"%s\",\"password\":\"Password123\"}"
                                    .formatted(escape(payload))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CREDENTIALS.code()));
        }

        assertThat(adminUserRepository.count()).isEqualTo(adminsBefore);
        // 注入尝试之后正常登录仍然可用，说明表结构与数据未被破坏
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"sqli-admin\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString());
    }

    @Test
    void tagInjectionPayloadsAreRejectedByValidation() throws Exception {
        Tag java = createTag("Java", "java");
        createArticle("safe-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now(), java);

        for (String payload : PAYLOADS) {
            mockMvc.perform(get(ARTICLES_URL).param("tag", payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                    .andExpect(jsonPath("$.data.tag").exists());
        }

        assertThat(articleRepository.count()).isEqualTo(1);
        mockMvc.perform(get(ARTICLES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void slugInjectionPayloadsReturnNoData() throws Exception {
        createArticle("safe-detail", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());
        long articlesBefore = articleRepository.count();

        for (String payload : PAYLOADS) {
            String encoded = URLEncoder.encode(payload, StandardCharsets.UTF_8);
            var result = mockMvc.perform(get(ARTICLES_URL + "/" + encoded)).andReturn();

            assertThat(result.getResponse().getStatus()).as("payload=%s", payload).isBetween(400, 499);
            assertThat(result.getResponse().getContentAsString())
                    .as("payload=%s 不能返回任何文章内容", payload)
                    .doesNotContain("password_hash")
                    .doesNotContain("safe-detail")
                    .doesNotContain("# Content");
        }

        assertThat(articleRepository.count()).isEqualTo(articlesBefore);
    }

    @Test
    void injectionPayloadInContentFieldsIsStoredLiterally() throws Exception {
        AdminUser admin = createAdmin("sqli-writer");
        String title = "Robert'); DROP TABLE article;--";
        String body = """
                {"title":"%s","slug":"literal-payload-post","content":"# normal content","status":"PUBLISHED"}
                """.formatted(escape(title));

        mockMvc.perform(post("/api/admin/articles")
                        .header(AUTH_HEADER, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value(title));

        Article stored = articleRepository.findBySlug("literal-payload-post").orElseThrow();
        assertThat(stored.getTitle()).isEqualTo(title);
        // 表依然可查：payload 作为数据存在，而不是被当作 SQL 执行
        assertThat(articleRepository.countByStatus(Article.ArticleStatus.PUBLISHED)).isEqualTo(1);
        assertThat(adminUserRepository.findByUsername("sqli-writer")).isPresent();
    }

    @Test
    void wildcardCharactersDoNotBehaveLikePatterns() throws Exception {
        Tag java = createTag("Java", "java");
        createArticle("wildcard-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now(), java);

        // 精确匹配：jav 不应命中 java
        mockMvc.perform(get(ARTICLES_URL).param("tag", "jav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // LIKE 通配符不是合法 tag 字符，直接被校验拦下
        mockMvc.perform(get(ARTICLES_URL).param("tag", "%"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.tag").exists());
        mockMvc.perform(get(ARTICLES_URL).param("tag", "java%"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repositoriesDeclareNoNativeSql() {
        List<Class<?>> repositories = List.of(
                AdminUserRepository.class, ArticleRepository.class, TagRepository.class, StaticAssetRepository.class);

        for (Class<?> repository : repositories) {
            for (Method method : repository.getMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query != null) {
                    assertThat(query.nativeQuery())
                            .as("%s.%s 不允许使用原生 SQL", repository.getSimpleName(), method.getName())
                            .isFalse();
                }
            }
        }
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
