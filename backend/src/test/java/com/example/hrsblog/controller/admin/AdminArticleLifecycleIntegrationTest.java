package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 管理端分页列表（含草稿、status 筛选）、全量更新（字段覆盖、slug 冲突、发布语义）、
 * 批量软删除（鉴权、部分成功、参数校验、id 去重、未选中文章不受影响）。
 *
 * <p>注意：同一个测试方法内的多个 MockMvc 请求共享测试事务的一级缓存，
 * 读回被软删除的文章前需要 {@code entityManager.clear()}，
 * 让下一次读取重新查库（与线上「一次请求一个持久化上下文」的行为保持一致）。
 */
class AdminArticleLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLES_URL = "/api/admin/articles";

    @Autowired
    private EntityManager entityManager;

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

        // 清空一级缓存：让后续读回操作重新查库，模拟一次全新的 HTTP 请求
        entityManager.clear();

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

        // 清空一级缓存：列表查询重新查库，updated_at（数据库默认值）才会被回填
        entityManager.clear();

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

    @Test
    void batchDeleteWithoutTokenIsUnauthorized() throws Exception {
        Article article = createArticle("batch-no-token-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        mockMvc.perform(post(ARTICLES_URL + "/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchDeleteJson(article.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));

        // 未认证时不得删除任何文章
        assertThat(articleRepository.existsBySlug("batch-no-token-post")).isTrue();
    }

    @Test
    void batchDeleteSoftDeletesAllSelectedArticles() throws Exception {
        var admin = createAdmin("batch-delete-admin");
        String authorization = bearer(admin);
        Article first = createArticle("batch-delete-a", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());
        Article second = createArticle("batch-delete-b", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        mockMvc.perform(post(ARTICLES_URL + "/batch-delete")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchDeleteJson(first.getId(), second.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failed.length()").value(0));

        // 清空一级缓存，让读回操作重新查库
        entityManager.clear();

        // 访客详情不可见
        mockMvc.perform(get("/api/visitors/articles/batch-delete-a"))
                .andExpect(status().isNotFound());

        // 管理端详情：已删除行对 @SQLRestriction 不可见，返回 40401
        mockMvc.perform(get(ARTICLES_URL + "/" + first.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()));

        // slug 已释放：可再次创建同名 slug 文章（若未释放会命中唯一索引 → 409）
        mockMvc.perform(post(ARTICLES_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(articleJson("batch-delete-a", "PUBLISHED")))
                .andExpect(status().isCreated());
    }

    @Test
    void batchDeleteKeepsSuccessesWhenSomeIdsAreMissing() throws Exception {
        var admin = createAdmin("batch-partial-admin");
        String authorization = bearer(admin);
        Article article = createArticle("batch-partial-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        mockMvc.perform(post(ARTICLES_URL + "/batch-delete")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchDeleteJson(article.getId(), 999_999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failed.length()").value(1))
                .andExpect(jsonPath("$.data.failed[0].id").value(999999))
                .andExpect(jsonPath("$.data.failed[0].code").value(ErrorCode.ARTICLE_NOT_FOUND.code()))
                .andExpect(jsonPath("$.data.failed[0].message").value(ErrorCode.ARTICLE_NOT_FOUND.message()));

        // 部分成功不会被整体回滚：真实文章确实已被软删除
        entityManager.clear();
        mockMvc.perform(get(ARTICLES_URL + "/" + article.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()));
    }

    @Test
    void batchDeleteWithEmptyIdsReturnsValidationError() throws Exception {
        var admin = createAdmin("batch-empty-admin");

        mockMvc.perform(post(ARTICLES_URL + "/batch-delete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.ids").exists());
    }

    @Test
    void batchDeleteWithMissingIdsFieldReturnsValidationError() throws Exception {
        var admin = createAdmin("batch-missing-ids-admin");

        mockMvc.perform(post(ARTICLES_URL + "/batch-delete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.ids").exists());
    }

    @Test
    void batchDeleteWithMoreThan200IdsReturnsValidationError() throws Exception {
        var admin = createAdmin("batch-too-many-admin");
        long[] ids = new long[201];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i + 1L;
        }

        mockMvc.perform(post(ARTICLES_URL + "/batch-delete")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchDeleteJson(ids)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.ids").exists());
    }

    @Test
    void batchDeleteDeduplicatesRepeatedIds() throws Exception {
        var admin = createAdmin("batch-duplicate-admin");
        String authorization = bearer(admin);
        Article article = createArticle("batch-duplicate-post", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        mockMvc.perform(post(ARTICLES_URL + "/batch-delete")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchDeleteJson(article.getId(), article.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failed.length()").value(0));
    }

    @Test
    void batchDeleteDoesNotDeleteUnselectedArticles() throws Exception {
        var admin = createAdmin("batch-untouched-admin");
        String authorization = bearer(admin);
        Article deleted = createArticle("batch-untouched-deleted", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());
        Article kept = createArticle("batch-untouched-kept", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());

        mockMvc.perform(post(ARTICLES_URL + "/batch-delete")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchDeleteJson(deleted.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failed.length()").value(0));

        // 未被选中的文章仍然可见
        entityManager.clear();
        mockMvc.perform(get(ARTICLES_URL + "/" + kept.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(kept.getId()))
                .andExpect(jsonPath("$.data.slug").value("batch-untouched-kept"));
    }

    /** 构造批量删除请求体：{@code {"ids":[1,2,...]}}。 */
    private String batchDeleteJson(long... ids) {
        StringBuilder array = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                array.append(',');
            }
            array.append(ids[i]);
        }
        array.append(']');
        return "{\"ids\":" + array + "}";
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
