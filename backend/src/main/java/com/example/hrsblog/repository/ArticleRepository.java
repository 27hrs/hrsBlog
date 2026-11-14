package com.example.hrsblog.repository;

import com.example.hrsblog.domain.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 文章仓储。
 *
 * <p>所有查询都是 Spring Data 派生方法，参数通过绑定变量传递，
 * 排序与分页由 {@link Pageable} 控制，杜绝字符串拼接 SQL / JPQL。
 */
public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsBySlug(String slug);

    Optional<Article> findBySlug(String slug);

    /** 访客详情：只有 PUBLISHED 才可能命中。 */
    Optional<Article> findByStatusAndSlug(Article.ArticleStatus status, String slug);

    /** 访客列表（不按标签过滤）：按 published_at 倒序由调用方通过 Pageable 指定。 */
    Page<Article> findByStatus(Article.ArticleStatus status, Pageable pageable);

    /** 访客列表（按标签 slug 过滤），tags.slug 走关联查询，仍是绑定参数。 */
    Page<Article> findByStatusAndTagsSlug(Article.ArticleStatus status, String tagSlug, Pageable pageable);

    long countByStatus(Article.ArticleStatus status);
}
