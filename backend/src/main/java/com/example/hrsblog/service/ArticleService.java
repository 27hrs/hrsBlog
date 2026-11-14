package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.HashUtils;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.domain.Tag;
import com.example.hrsblog.dto.ArticleCreateRequest;
import com.example.hrsblog.dto.ArticleDetailResponse;
import com.example.hrsblog.dto.ArticleQuery;
import com.example.hrsblog.dto.ArticleSummaryResponse;
import com.example.hrsblog.dto.TagResponse;
import com.example.hrsblog.repository.ArticleRepository;
import com.example.hrsblog.repository.TagRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文章业务：管理员投递 + 访客只读视图。
 *
 * <p>可见性规则集中在服务层：访客列表/详情只查询 {@code status = PUBLISHED}，
 * 草稿与不存在的 slug 对外表现一致（404），不泄露草稿存在性。
 */
@Slf4j
@Service
public class ArticleService {

    private static final String DEFAULT_CONTENT_FORMAT = "MARKDOWN";
    private static final int MAX_TAGS = 10;

    private final ArticleRepository articleRepository;
    private final TagRepository tagRepository;
    private final EntityManager entityManager;

    public ArticleService(ArticleRepository articleRepository, TagRepository tagRepository, EntityManager entityManager) {
        this.articleRepository = articleRepository;
        this.tagRepository = tagRepository;
        this.entityManager = entityManager;
    }

    /** 管理员投递文章：status=PUBLISHED 时写入 published_at，slug 冲突返回 409。 */
    @Transactional
    public ArticleDetailResponse create(ArticleCreateRequest request) {
        String slug = request.slug().trim();
        if (articleRepository.existsBySlug(slug)) {
            throw new BusinessException(ErrorCode.SLUG_CONFLICT);
        }

        Article article = new Article();
        article.setTitle(request.title().trim());
        article.setSlug(slug);
        article.setSummary(trimToNull(request.summary()));
        article.setContent(request.content());
        article.setContentFormat(StringUtils.hasText(request.contentFormat())
                ? request.contentFormat().trim().toUpperCase(Locale.ROOT)
                : DEFAULT_CONTENT_FORMAT);
        article.setCoverUrl(trimToNull(request.coverUrl()));

        Article.ArticleStatus status = Article.ArticleStatus.valueOf(request.status());
        article.setStatus(status);
        article.setPublishedAt(status == Article.ArticleStatus.PUBLISHED ? LocalDateTime.now() : null);
        article.setTags(resolveTags(request.tags()));

        Article saved;
        try {
            saved = articleRepository.saveAndFlush(article);
        } catch (DataIntegrityViolationException ex) {
            // 并发投递同一 slug 时由数据库唯一索引兜底
            throw new BusinessException(ErrorCode.SLUG_CONFLICT, ErrorCode.SLUG_CONFLICT.message(), ex);
        }
        // created_at / updated_at 由数据库默认值生成，refresh 后回填到响应
        entityManager.refresh(saved);
        log.info("文章已投递 slug={} status={} articleId={}", saved.getSlug(), saved.getStatus(), saved.getId());
        return toDetail(saved);
    }

    /** 访客文章分页：固定按 published_at 倒序，size 上限由 DTO 校验。 */
    @Transactional(readOnly = true)
    public PageResponse<ArticleSummaryResponse> listPublished(ArticleQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
        Page<Article> page = StringUtils.hasText(query.getTag())
                ? articleRepository.findByStatusAndTagsSlug(Article.ArticleStatus.PUBLISHED, query.getTag().trim(), pageable)
                : articleRepository.findByStatus(Article.ArticleStatus.PUBLISHED, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    /** 访客文章详情：仅 PUBLISHED 可见，草稿与不存在都返回 404。 */
    @Transactional(readOnly = true)
    public ArticleDetailResponse getPublishedBySlug(String slug) {
        return articleRepository.findByStatusAndSlug(Article.ArticleStatus.PUBLISHED, slug)
                .map(this::toDetail)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
    }

    private Set<Tag> resolveTags(Set<String> requestedTags) {
        Set<Tag> tags = new LinkedHashSet<>();
        if (requestedTags == null || requestedTags.isEmpty()) {
            return tags;
        }
        Map<String, Tag> resolved = new LinkedHashMap<>();
        for (String raw : requestedTags) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String name = raw.trim();
            if (name.length() > 50) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "标签名长度不能超过 50");
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (resolved.containsKey(key)) {
                continue;
            }
            Tag tag = tagRepository.findByName(name).orElseGet(() -> createTag(name));
            resolved.put(key, tag);
            tags.add(tag);
        }
        if (tags.size() > MAX_TAGS) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "标签数量不能超过 " + MAX_TAGS);
        }
        return tags;
    }

    private Tag createTag(String name) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(uniqueTagSlug(name));
        return tagRepository.save(tag);
    }

    private String uniqueTagSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;
        while (tagRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
            if (suffix > 100) {
                throw new BusinessException(ErrorCode.DATA_CONFLICT, "无法为标签生成唯一 slug");
            }
        }
        return candidate;
    }

    private String slugify(String name) {
        String normalized = Normalizer.normalize(name.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFKD)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (normalized.length() > 60) {
            normalized = normalized.substring(0, 60).replaceAll("-+$", "");
        }
        return normalized.isEmpty() ? "tag-" + HashUtils.sha256Hex(name).substring(0, 8) : normalized;
    }

    private ArticleSummaryResponse toSummary(Article article) {
        return new ArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getSummary(),
                article.getCoverUrl(),
                article.getPublishedAt(),
                toTagResponses(article));
    }

    private ArticleDetailResponse toDetail(Article article) {
        return new ArticleDetailResponse(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getSummary(),
                article.getContent(),
                article.getContentFormat(),
                article.getCoverUrl(),
                article.getStatus().name(),
                article.getPublishedAt(),
                article.getCreatedAt(),
                article.getUpdatedAt(),
                toTagResponses(article));
    }

    private List<TagResponse> toTagResponses(Article article) {
        return article.getTags().stream()
                .sorted(Comparator.comparing(Tag::getName))
                .map(tag -> new TagResponse(tag.getName(), tag.getSlug()))
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
