package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.domain.Tag;
import com.example.hrsblog.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 访客接口集成测试：分页边界、published_at 倒序、草稿不可见、404 与 slug 校验。
 */
class VisitorArticleControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLES_URL = "/api/visitors/articles";
    private static final String SLUG_REGEX = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private Tag javaTag;

    @BeforeEach
    void seedArticles() {
        javaTag = createTag("Java", "java");
        LocalDateTime now = LocalDateTime.now();
        createArticle("oldest-post", Article.ArticleStatus.PUBLISHED, now.minusDays(3));
        createArticle("middle-post", Article.ArticleStatus.PUBLISHED, now.minusDays(2), javaTag);
        createArticle("newest-post", Article.ArticleStatus.PUBLISHED, now.minusDays(1), javaTag);
        createArticle("draft-post", Article.ArticleStatus.DRAFT, null);
    }

    @Test
    void listsOnlyPublishedArticlesNewestFirst() throws Exception {
        mockMvc.perform(get(ARTICLES_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].slug").value("newest-post"))
                .andExpect(jsonPath("$.data.items[1].slug").value("middle-post"))
                .andExpect(jsonPath("$.data.items[2].slug").value("oldest-post"))
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].tags[0].slug").value("java"));
    }

    @Test
    void paginationRespectsSizeAndPage() throws Exception {
        mockMvc.perform(get(ARTICLES_URL).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get(ARTICLES_URL).param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].slug").value("oldest-post"));
    }

    @Test
    void rejectsSizeAboveFifty() throws Exception {
        mockMvc.perform(get(ARTICLES_URL).param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.size").exists());
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get(ARTICLES_URL).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.page").exists());
    }

    @Test
    void rejectsNonNumericPage() throws Exception {
        // 查询参数类型不合法：绑定阶段就会失败（字段级校验或通用 400 均可，但绝不能落到 500 或静默忽略）
        mockMvc.perform(get(ARTICLES_URL).param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filtersByTagSlug() throws Exception {
        mockMvc.perform(get(ARTICLES_URL).param("tag", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].slug").value("newest-post"));
    }

    @Test
    void tagWithoutMatchReturnsEmptyPageInsteadOfWildcardMatch() throws Exception {
        mockMvc.perform(get(ARTICLES_URL).param("tag", "jav"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void rejectsTagWithIllegalCharacters() throws Exception {
        mockMvc.perform(get(ARTICLES_URL).param("tag", "java' OR '1'='1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.tag").exists());
    }

    @Test
    void returnsPublishedArticleDetailWithContentAndTags() throws Exception {
        mockMvc.perform(get(ARTICLES_URL + "/newest-post"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("newest-post"))
                .andExpect(jsonPath("$.data.content").value("# Content newest-post"))
                .andExpect(jsonPath("$.data.contentFormat").value("MARKDOWN"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.publishedAt").exists())
                .andExpect(jsonPath("$.data.tags.length()").value(1));
    }

    @Test
    void draftArticleIsNotVisibleToVisitors() throws Exception {
        mockMvc.perform(get(ARTICLES_URL + "/draft-post"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()))
                .andExpect(jsonPath("$.message").value(ErrorCode.ARTICLE_NOT_FOUND.message()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unknownSlugReturnsNotFound() throws Exception {
        mockMvc.perform(get(ARTICLES_URL + "/not-exists"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.ARTICLE_NOT_FOUND.code()));
    }

    @Test
    void slugWithIllegalFormatIsRejectedByValidation() throws Exception {
        mockMvc.perform(get(ARTICLES_URL + "/Invalid_Slug"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.slug").exists());
    }

    @Test
    void tooLongSlugIsRejectedByValidation() throws Exception {
        String slug = "a".repeat(201);

        mockMvc.perform(get(ARTICLES_URL + "/" + slug))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.slug").exists());
    }

    @Test
    void slugPatternConstantMatchesDocumentedContract() {
        // 文档中承诺的 slug 规则：小写字母/数字/单连字符，1-200 位
        assertThat("spring-boot-guide").matches(SLUG_REGEX);
        assertThat("a").matches(SLUG_REGEX);
        assertThat("post-2026-01").matches(SLUG_REGEX);
        assertThat("Spring-Boot").doesNotMatch(SLUG_REGEX);
        assertThat("-leading").doesNotMatch(SLUG_REGEX);
        assertThat("trailing-").doesNotMatch(SLUG_REGEX);
        assertThat("double--dash").doesNotMatch(SLUG_REGEX);
    }
}
