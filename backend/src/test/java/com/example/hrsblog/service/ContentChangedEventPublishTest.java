package com.example.hrsblog.service;

import com.example.hrsblog.domain.Article;
import com.example.hrsblog.domain.SiteSetting;
import com.example.hrsblog.dto.ArticleCreateRequest;
import com.example.hrsblog.dto.ArticleUpdateRequest;
import com.example.hrsblog.event.ContentChangedEvent;
import com.example.hrsblog.repository.ArticleRepository;
import com.example.hrsblog.repository.SiteSettingRepository;
import com.example.hrsblog.repository.TagRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 内容变更埋点测试：哪些操作会发布 {@link ContentChangedEvent}，哪些不会。
 *
 * <p>规则（与产品约定一致）：
 * <ul>
 *   <li>站点配置实际发生变化 → 触发 {@code settings_updated}；保存相同值不触发；</li>
 *   <li>新建即发布 / 草稿首次转已发布 → 触发 {@code article_published}；</li>
 *   <li>已发布文章内容更新 → 触发 {@code article_updated}；</li>
 *   <li>草稿的新建与编辑 → 不触发。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ContentChangedEventPublishTest {

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SiteSettingService siteSettingService;
    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        siteSettingService = new SiteSettingService(siteSettingRepository, new ObjectMapper(), eventPublisher);
        articleService = new ArticleService(articleRepository, tagRepository, entityManager, eventPublisher);
    }

    // ── 站点配置 ──────────────────────────────────────────

    @Test
    void unchangedSettingsDoNotPublishEvent() {
        when(siteSettingRepository.findAllByOrderByConfigGroupAscSortOrderAsc())
                .thenReturn(List.of(setting("basic", "siteName", "HrsBlog")));

        siteSettingService.replaceAll(Map.of("siteName", "HrsBlog"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changedSettingsPublishEvent() {
        when(siteSettingRepository.findAllByOrderByConfigGroupAscSortOrderAsc())
                .thenReturn(List.of(setting("basic", "siteName", "HrsBlog")));

        siteSettingService.replaceAll(Map.of("siteName", "HrsBlog 2"));

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_SETTINGS_UPDATED);
    }

    @Test
    void newlyAddedSettingWithValuePublishesEvent() {
        when(siteSettingRepository.findAllByOrderByConfigGroupAscSortOrderAsc())
                .thenReturn(List.of());

        siteSettingService.replaceAll(Map.of("webhookUrl", "https://example.test/dispatch"));

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_SETTINGS_UPDATED);
    }

    @Test
    void newlyAddedSettingWithEmptyValueDoesNotPublishEvent() {
        when(siteSettingRepository.findAllByOrderByConfigGroupAscSortOrderAsc())
                .thenReturn(List.of());

        siteSettingService.replaceAll(Map.of("webhookUrl", ""));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void removedSettingCountsAsChange() {
        when(siteSettingRepository.findAllByOrderByConfigGroupAscSortOrderAsc())
                .thenReturn(List.of(setting("basic", "siteName", "HrsBlog")));

        siteSettingService.replaceAll(Map.of());

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_SETTINGS_UPDATED);
    }

    // ── 文章 ──────────────────────────────────────────────

    @Test
    void publishedArticleCreationPublishesEvent() {
        when(articleRepository.existsBySlug("hello")).thenReturn(false);
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        articleService.create(createRequest("hello", "PUBLISHED"));

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_ARTICLE_PUBLISHED);
    }

    @Test
    void draftArticleCreationDoesNotPublishEvent() {
        when(articleRepository.existsBySlug("draft")).thenReturn(false);
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        articleService.create(createRequest("draft", "DRAFT"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void firstPublicationOfDraftPublishesEvent() {
        Article draft = article("draft-to-published", Article.ArticleStatus.DRAFT, null);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(draft));
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        articleService.updateArticle(1L, updateRequest("draft-to-published", "PUBLISHED"));

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_ARTICLE_PUBLISHED);
    }

    @Test
    void updatingPublishedArticlePublishesArticleUpdatedEvent() {
        Article published = article("already-published", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());
        when(articleRepository.findById(2L)).thenReturn(Optional.of(published));
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        articleService.updateArticle(2L, updateRequest("already-published", "PUBLISHED"));

        assertThat(publishedReasons()).containsExactly(ContentChangedEvent.REASON_ARTICLE_UPDATED);
    }

    @Test
    void editingDraftDoesNotPublishEvent() {
        Article draft = article("still-draft", Article.ArticleStatus.DRAFT, null);
        when(articleRepository.findById(3L)).thenReturn(Optional.of(draft));
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        articleService.updateArticle(3L, updateRequest("still-draft", "DRAFT"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changingPublishedArticleBackToDraftDoesNotPublishEvent() {
        Article published = article("back-to-draft", Article.ArticleStatus.PUBLISHED, LocalDateTime.now());
        when(articleRepository.findById(4L)).thenReturn(Optional.of(published));
        when(articleRepository.saveAndFlush(any(Article.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        articleService.updateArticle(4L, updateRequest("back-to-draft", "DRAFT"));

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ── 辅助 ──────────────────────────────────────────────

    private List<String> publishedReasons() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        Object event = captor.getValue();
        assertThat(event).isInstanceOf(ContentChangedEvent.class);
        return ((ContentChangedEvent) event).reasons();
    }

    private SiteSetting setting(String group, String key, String value) {
        SiteSetting setting = new SiteSetting();
        setting.setId(1L);
        setting.setConfigGroup(group);
        setting.setConfigKey(key);
        setting.setConfigValue(value);
        setting.setValueType(SiteSetting.ValueType.STRING);
        setting.setPublicSetting(true);
        setting.setSortOrder(1);
        return setting;
    }

    private Article article(String slug, Article.ArticleStatus status, LocalDateTime publishedAt) {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("Title " + slug);
        article.setSlug(slug);
        article.setSummary("Summary");
        article.setContent("# " + slug);
        article.setContentFormat("MARKDOWN");
        article.setStatus(status);
        article.setPublishedAt(publishedAt);
        return article;
    }

    private ArticleCreateRequest createRequest(String slug, String status) {
        return new ArticleCreateRequest("Title " + slug, slug, "Summary", "# content", null, null, status, Set.of());
    }

    private ArticleUpdateRequest updateRequest(String slug, String status) {
        return new ArticleUpdateRequest("Title " + slug, slug, "Summary", "# content", null, null, status, Set.of());
    }
}
