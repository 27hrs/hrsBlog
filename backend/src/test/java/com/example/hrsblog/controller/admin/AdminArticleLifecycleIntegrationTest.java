package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员文章生命周期集成测试：软删除（不可见性、slug 释放、幂等语义）、
 * 管理端分页列表（含草稿、status 筛选）、全量更新（字段覆盖、slug 冲突、发布语义）。
 */
class AdminArticleLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLES_URL = "/api/admin/articles";

    @Test
    void deleteWithoutTokenIsUnauthorized() throws Exception {
        Article article = createArticle("delete-no-token-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        mockMvc.perform(delete(ARTICLES_URL + "/" + article.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
        assertThat(articleRepository.existsBySlug("delete-no-token-post")).isTrue();
    }

    @Test
    void deleteMissingArticleReturnsArticleNotFound() throws Exception {
        var admin = createAdmin("delete-admin");

        mockMvc.perform(delete(ARTICLES_URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ARTICLE_NOT_FOUND.message()));
    }

    @Test
    void deleteSoftDeletesHidesArticleAndReleasesSlug() throws Exception {
        var admin = createAdmin("soft-delete-admin");
        String authorization = bearer(admin);
        Article article = createArticle("soft-delete-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        mockMvc.perform(delete(ARTICLES_URL + "/" + article.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("文章已删除"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 访客详情与列表均不可见
        mockMvc.perform(get("/api/visitors/articles/soft-delete-post"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/visitors/articles?page=0&size=10"))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // 管理端详情/列表/重复删除：已删除行对 @SQLRestriction 不可见，统一 40401
        mockMvc.perform(get(ARTICLES_URL + "/" + article.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()));
        mockMvc.perform(delete(ARTICLES_URL + "/" + article.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()));

        // slug 已释放：可再次创建同名 slug 文章（若未释放会命中唯一索引 → 409）
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("soft-delete-post", "PUBLISHED")))
                .andExpect(status().isCreated());
    }

    @Test
    void adminListReturnsDraftsAndPublishedWithPaginationAndStatusFilter() throws Exception {
        var admin = createAdmin("list-admin");
        String authorization = bearer(admin);

        createArticle("list-draft-a", Article.ArticleStatus.DRAFT, null);
        createArticle("list-published-a", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());
        createArticle("list-published-b", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        mockMvc.perform(get(ARTICLES_URL + "?page=0&size=2")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].status").exists())
                .andExpect(jsonPath("$.data.items[0].updatedAt").exists());

        mockMvc.perform(get(ARTICLES_URL + "?page=0&size=10&status=DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].slug").value("list-draft-a"))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"));

        mockMvc.perform(get(ARTICLES_URL + "?page=0&size=10&status=PUBLISHED")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get(ARTICLES_URL + "?page=0&size=10&status=ARCHIVED")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()));
    }

    @Test
    void updateReplacesAllFieldsAndKeepsExistingPublishedAt() throws Exception {
        var admin = createAdmin("update-admin");
        String authorization = bearer(admin);
        LocalDateTime originalPublishedAt = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS);
        Article article = createArticle("update-post", Article.ArticleStatus.PUBLISHED, originalPublishedAt,
                createTag("Old", "old-tag"));

        mockMvc.perform(put(ARTICLES_URL + "/" + article.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("update-post", "PUBLISHED", "Java", "Spring Boot")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(article.getId()))
                .andExpect(jsonPath("$.data.title").value("Updated title"))
                .andExpect(jsonPath("$.data.summary").value("Updated summary"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.tags.length()").value(2))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        Article saved = articleRepository.findById(article.getId()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("Updated title");
        assertThat(saved.getSummary()).isEqualTo("Updated summary");
        assertThat(saved.getContent()).contains("Updated content");
        assertThat(saved.getPublishedAt()).isEqualTo(originalPublishedAt);
        assertThat(saved.getTags()).extracting("name").containsExactlyInAnyOrder("Java", "Spring Boot");
    }

    @Test
    void updateDraftToPublishedBackfillsPublishedAt() throws Exception {
        var admin = createAdmin("publish-admin");
        Article article = createArticle("draft-to-published", Article.ArticleStatus.DRAFT, null);

        assertThat(article.getPublishedAt()).isNull();

        mockMvc.perform(put(ARTICLES_URL + "/" + article.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("draft-to-published", "PUBLISHED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists());

        assertThat(articleRepository.findById(article.getId()).orElseThrow().getPublishedAt()).isNotNull();
    }

    @Test
    void updateWithSlugOfAnotherArticleReturnsConflictButKeepsOwnSlug() throws Exception {
        var admin = createAdmin("slug-admin");
        String authorization = bearer(admin);
        Article first = createArticle("first-slug-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());
        Article second = createArticle("second-slug-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        // 修改为他人 slug → 409
        mockMvc.perform(put(ARTICLES_URL + "/" + first.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("second-slug-post", "PUBLISHED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SLUG_CONFLICT.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.SLUG_CONFLICT.message()));

        // 保持自身 slug → 200（预检排除自身）
        mockMvc.perform(put(ARTICLES_URL + "/" + second.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("second-slug-post", "PUBLISHED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("second-slug-post"));
    }

    @Test
    void updateMissingArticleReturnsArticleNotFoundAndValidationStillApplies() throws Exception {
        var admin = createAdmin("update-missing-admin");
        String authorization = bearer(admin);

        mockMvc.perform(put(ARTICLES_URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("missing-post", "PUBLISHED")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()));

        mockMvc.perform(put(ARTICLES_URL + "/0")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson("Bad Slug!", "PUBLISHED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.slug").exists());
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
                """.formatted(slug, slug, slug, slug, status);
    }

    private String updateJson(String slug, String status, String... tags) {
        StringBuilder tagArray = new StringBuilder("[");
        for (int i = 0; i < tags.length; i++) {
            if (i > 0) {
                tagArray.append(',');
            }
            tagArray.append('"').append(tags[i]).append('"');
        }
        tagArray.append(']');

        return """
                {
                  "title": "Updated title",
                  "slug": "%s",
                  "summary": "Updated summary",
                  "content": "# Updated content",
                  "contentFormat": "MARKDOWN",
                  "status": "%s",
                  "tags": %s
                }
                """.formatted(slug, status, tagArray);
    }
}
