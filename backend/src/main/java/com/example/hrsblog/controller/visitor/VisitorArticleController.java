package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.dto.ArticleDetailResponse;
import com.example.hrsblog.dto.ArticleQuery;
import com.example.hrsblog.dto.ArticleSummaryResponse;
import com.example.hrsblog.service.ArticleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访客文章接口（匿名可访问）。
 *
 * <ul>
 *   <li>{@code GET /api/visitors/articles}：分页列表，仅返回 PUBLISHED，按发布时间倒序；</li>
 *   <li>{@code GET /api/visitors/articles/{slug}}：详情，未发布或不存在统一 404。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/visitors/articles")
@RequiredArgsConstructor
public class VisitorArticleController {

    private static final String SLUG_REGEX = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private final ArticleService articleService;

    @GetMapping
    public ApiResponse<PageResponse<ArticleSummaryResponse>> list(@Valid @ModelAttribute ArticleQuery query) {
        return ApiResponse.ok(articleService.listPublished(query));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ArticleDetailResponse> detail(
            @PathVariable
            @Size(max = 200, message = "slug 长度不能超过 200")
            @Pattern(regexp = SLUG_REGEX, message = "slug 只能由小写字母、数字和单个连字符组成")
            String slug) {
        return ApiResponse.ok(articleService.getPublishedBySlug(slug));
    }
}
