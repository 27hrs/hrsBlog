package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员投递文章集成测试：鉴权（无 token / 伪造 / 过期）、201 投递、409 slug 冲突、字段级校验。
 */
class AdminArticleControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLES_URL = "/api/admin/articles";

    private AdminUser admin;
    private String authorization;

    @BeforeEach
    void setUpAdmin() {
        admin = createAdmin("article-admin");
        authorization = bearer(admin);
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("no-token-post", "PUBLISHED")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
        assertThat(articleRepository.existsBySlug("no-token-post")).isFalse();
    }

    @Test
    void rejectsMalformedToken() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .header(AUTH_HEADER, "Bearer not-a-valid-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("malformed-token-post", "PUBLISHED")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() throws Exception {
        String forged = "Bearer " + Jwts.builder()
                .subject(admin.getUsername())
                .issuer("hrsblog-test")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .signWith(Keys.hmacShaKeyFor("attacker-secret-0123456789-0123456789-xyz".getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(post(ARTICLES_URL)
                        .header(AUTH_HEADER, forged)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("forged-token-post", "PUBLISHED")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String expired = "Bearer " + Jwts.builder()
                .subject(admin.getUsername())
                .issuer("hrsblog-test")
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(
                        "test-only-jwt-secret-please-do-not-use-in-production-0123456789".getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(post(ARTICLES_URL)
                        .header(AUTH_HEADER, expired)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("expired-token-post", "PUBLISHED")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void publishesArticleAndMakesItVisibleToVisitors() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("published-post", "PUBLISHED")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("published-post"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.contentFormat").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.publishedAt").exists())
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andExpect(jsonPath("$.data.tags[0].name").value("Java"))
                .andExpect(jsonPath("$.data.tags[1].name").value("Spring Boot"));

        assertThat(tagRepository.findByName("Java")).isPresent();
        assertThat(tagRepository.findByName("Spring Boot").orElseThrow().getSlug()).isEqualTo("spring-boot");

        mockMvc.perform(get("/api/visitors/articles/published-post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("published-post"));
    }

    @Test
    void savesDraftWithoutPublishedAtAndKeepsItHidden() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("draft-only-post", "DRAFT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.publishedAt").doesNotExist());

        assertThat(articleRepository.findBySlug("draft-only-post").orElseThrow().getStatus())
                .isEqualTo(Article.ArticleStatus.DRAFT);

        mockMvc.perform(get("/api/visitors/articles/draft-only-post"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateSlugReturnsConflict() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("duplicated-post", "PUBLISHED")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("duplicated-post", "PUBLISHED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SLUG_CONFLICT.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SLUG_CONFLICT.message()));
    }

    @Test
    void blankTitleIsRejectedWithFieldLevelError() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","slug":"blank-title-post","content":"# c","status":"PUBLISHED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.title").exists());
    }

    @Test
    void illegalSlugFormatIsRejectedWithFieldLevelError() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("Illegal Slug!", "PUBLISHED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.slug").exists());
        assertThat(articleRepository.existsBySlug("Illegal Slug!")).isFalse();
    }

    @Test
    void blankContentIsRejected() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"No content","slug":"no-content-post","content":" ","status":"PUBLISHED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.content").exists());
    }

    @Test
    void unsupportedStatusAndContentFormatAreRejected() throws Exception {
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad status","slug":"bad-status-post","content":"# c","status":"ARCHIVED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").exists());

        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Bad format","slug":"bad-format-post","content":"# c",
                                 "status":"PUBLISHED","contentFormat":"HTML"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.contentFormat").exists());
    }

    @Test
    void tooManyTagsAreRejected() throws Exception {
        StringBuilder tags = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            if (i > 0) {
                tags.append(',');
            }
            tags.append('"').append("tag-").append(i).append('"');
        }
        String body = """
                {"title":"Many tags","slug":"many-tags-post","content":"# c",
                 "status":"PUBLISHED","tags":[%s]}
                """.formatted(tags);

        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.tags").exists());
    }

    @Test
    void injectionPayloadInTitleIsStoredLiterallyAndTablesSurvive() throws Exception {
        String payload = "Robert'); DROP TABLE article;--";
        String summaryPayload = "' OR 1=1 --";
        String body = """
                {"title":"%s","slug":"injection-title-post","summary":"%s","content":"# c","status":"PUBLISHED"}
                """.formatted(escape(payload), escape(summaryPayload));

        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title").value(payload))
                .andExpect(jsonPath("$.data.summary").value(summaryPayload));

        assertThat(articleRepository.findBySlug("injection-title-post").orElseThrow().getTitle()).isEqualTo(payload);
        assertThat(articleRepository.countByStatus(Article.ArticleStatus.PUBLISHED)).isEqualTo(1);
        assertThat(adminUserRepository.count()).isEqualTo(1);
    }

    private String articleJson(String slug, String status) {
        return """
                {
                  "title": "Title for %s",
                  "slug": "%s",
                  "summary": "Summary for %s",
                  "content": "# Content for %s",
                  "contentFormat": "MARKDOWN",
                  "status": "%s",
                  "tags": ["Java", "Spring Boot"]
                }
                """.formatted(escape(slug), escape(slug), escape(slug), escape(slug), status);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
