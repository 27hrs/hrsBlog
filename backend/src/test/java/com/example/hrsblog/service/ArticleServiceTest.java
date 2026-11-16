package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.domain.Tag;
import com.example.hrsblog.dto.ArticleCreateRequest;
import com.example.hrsblog.dto.ArticleDetailResponse;
import com.example.hrsblog.dto.ArticleQuery;
import com.example.hrsblog.dto.ArticleSummaryResponse;
import com.example.hrsblog.repository.ArticleRepository;
import com.example.hrsblog.repository.TagRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 文章业务单元测试：slug 冲突、发布时间、草稿不可见、标签复用与列表排序。 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ArticleService articleService;

    @Test
    void publishedArticleGetsPublishedAtAndDefaultContentFormat() {
        when(articleRepository.existsBySlug("hello-world")).thenReturn(false);
        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleDetailResponse response = articleService.create(request("hello-world", "PUBLISHED", null, Set.of()));

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).saveAndFlush(captor.capture());
        Article saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(Article.ArticleStatus.PUBLISHED);
        assertThat(saved.getPublishedAt()).isNotNull();
        assertThat(saved.getContentFormat()).isEqualTo("MARKDOWN");
        assertThat(response.status()).isEqualTo("PUBLISHED");
        assertThat(response.publishedAt()).isNotNull();
    }

    @Test
    void draftArticleHasNoPublishedAt() {
        when(articleRepository.existsBySlug("draft-article")).thenReturn(false);
        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ArticleDetailResponse response = articleService.create(request("draft-article", "DRAFT", null, Set.of()));

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    void duplicateSlugIsRejectedWithConflictBeforeInsert() {
        when(articleRepository.existsBySlug("duplicated")).thenReturn(true);

        assertThatThrownBy(() -> articleService.create(request("duplicated", "PUBLISHED", null, Set.of())))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SLUG_CONFLICT));
        verify(articleRepository, never()).saveAndFlush(any());
    }

    @Test
    void unknownTagIsCreatedWithDerivedSlug() {
        when(articleRepository.existsBySlug("with-tags")).thenReturn(false);
        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName("Spring Boot")).thenReturn(Optional.empty());
        when(tagRepository.existsBySlug("spring-boot")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        articleService.create(request("with-tags", "PUBLISHED", null, Set.of("Spring Boot")));

        ArgumentCaptor<Tag> tagCaptor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(tagCaptor.capture());
        assertThat(tagCaptor.getValue().getName()).isEqualTo("Spring Boot");
        assertThat(tagCaptor.getValue().getSlug()).isEqualTo("spring-boot");
    }

    @Test
    void existingTagIsReusedInsteadOfCreated() {
        Tag existing = tag("Java", "java");
        when(articleRepository.existsBySlug("reuse-tag")).thenReturn(false);
        when(articleRepository.saveAndFlush(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByName("Java")).thenReturn(Optional.of(existing));

        articleService.create(request("reuse-tag", "PUBLISHED", null, Set.of("Java")));

        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    void visitorDetailOnlyLooksUpPublishedArticles() {
        when(articleRepository.findByStatusAndSlug(Article.ArticleStatus.PUBLISHED, "draft-only"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.getPublishedBySlug("draft-only"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ARTICLE_NOT_FOUND));
        verify(articleRepository).findByStatusAndSlug(Article.ArticleStatus.PUBLISHED, "draft-only");
    }

    @Test
    void visitorDetailReturnsPublishedArticleWithoutStatusLeakageOfDraft() {
        Article article = publishedArticle("visible", LocalDateTime.now().minusHours(1));
        when(articleRepository.findByStatusAndSlug(Article.ArticleStatus.PUBLISHED, "visible"))
                .thenReturn(Optional.of(article));

        ArticleDetailResponse response = articleService.getPublishedBySlug("visible");

        assertThat(response.slug()).isEqualTo("visible");
        assertThat(response.content()).isEqualTo("# visible");
        assertThat(response.status()).isEqualTo("PUBLISHED");
    }

    @Test
    void visitorListQueriesOnlyPublishedAndSortsByPublishedAtDesc() {
        Article article = publishedArticle("sorted", LocalDateTime.now());
        when(articleRepository.findByStatus(eq(Article.ArticleStatus.PUBLISHED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(article)));

        PageResponse<ArticleSummaryResponse> response = articleService.listPublished(new ArticleQuery());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(articleRepository).findByStatus(eq(Article.ArticleStatus.PUBLISHED), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        Sort.Order order = pageable.getSort().getOrderFor("publishedAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().slug()).isEqualTo("sorted");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void visitorListFiltersByTagSlug() {
        ArticleQuery query = new ArticleQuery();
        query.setTag("java");
        when(articleRepository.findByStatusAndTagsSlug(eq(Article.ArticleStatus.PUBLISHED), eq("java"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(publishedArticle("tagged", LocalDateTime.now()))));

        PageResponse<ArticleSummaryResponse> response = articleService.listPublished(query);

        assertThat(response.items()).hasSize(1);
        verify(articleRepository).findByStatusAndTagsSlug(eq(Article.ArticleStatus.PUBLISHED), eq("java"), any(Pageable.class));
        verify(articleRepository, never()).findByStatus(any(), any());
    }

    @Test
    void tooManyTagsAreRejected() {
        when(articleRepository.existsBySlug("many-tags")).thenReturn(false);
        when(tagRepository.findByName(any())).thenReturn(Optional.empty());
        when(tagRepository.existsBySlug(any())).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Set<String> tags = new LinkedHashSet<>();
        for (int i = 0; i < 11; i++) {
            tags.add("tag-" + i);
        }

        assertThatThrownBy(() -> articleService.create(request("many-tags", "PUBLISHED", null, tags)))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        verify(articleRepository, never()).saveAndFlush(any());
    }

    private ArticleCreateRequest request(String slug, String status, String contentFormat, Set<String> tags) {
        return new ArticleCreateRequest(
                "Title of " + slug,
                slug,
                "Summary",
                "# content",
                contentFormat,
                null,
                status,
                tags);
    }

    private Article publishedArticle(String slug, LocalDateTime publishedAt) {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("Title of " + slug);
        article.setSlug(slug);
        article.setSummary("Summary");
        article.setContent("# " + slug);
        article.setContentFormat("MARKDOWN");
        article.setStatus(Article.ArticleStatus.PUBLISHED);
        article.setPublishedAt(publishedAt);
        article.setCreatedAt(publishedAt.minusDays(1));
        article.setUpdatedAt(publishedAt);
        article.setTags(new LinkedHashSet<>(Set.of(tag("Java", "java"))));
        return article;
    }

    private Tag tag(String name, String slug) {
        Tag tag = new Tag();
        tag.setId(1L);
        tag.setName(name);
        tag.setSlug(slug);
        return tag;
    }
}
