package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.dto.AdminArticleSummaryResponse;
import com.example.hrsblog.dto.ArticleCreateRequest;
import com.example.hrsblog.dto.ArticleDetailResponse;
import com.example.hrsblog.dto.ArticleQuery;
import com.example.hrsblog.dto.ArticleUpdateRequest;
import com.example.hrsblog.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员文章接口，需要 ROLE_ADMIN（由 SecurityConfig 的 /api/admin/** 统一校验）。
 *
 * <ul>
 *   <li>{@code POST /api/admin/articles}：投递草稿或直接发布；</li>
 *   <li>{@code GET /api/admin/articles}：分页列表（草稿 + 已发布，支持 status 筛选）；</li>
 *   <li>{@code GET /api/admin/articles/{id}}：详情（编辑表单取数）；</li>
 *   <li>{@code PUT /api/admin/articles/{id}}：全量更新；</li>
 *   <li>{@code DELETE /api/admin/articles/{id}}：软删除。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/articles")
@RequiredArgsConstructor
public class AdminArticleController {

    private final ArticleService articleService;

    @PostMapping
    public ResponseEntity<ApiResponse<ArticleDetailResponse>> create(@Valid @RequestBody ArticleCreateRequest request) {
        ArticleDetailResponse created = articleService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminArticleSummaryResponse>> list(@Valid @ModelAttribute ArticleQuery query) {
        return ApiResponse.ok(articleService.listForAdmin(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<ArticleDetailResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(articleService.getForAdmin(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ArticleDetailResponse> update(@PathVariable("id") Long id,
            @Valid @RequestBody ArticleUpdateRequest request) {
        return ApiResponse.ok(articleService.updateArticle(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        articleService.deleteArticle(id);
        return new ApiResponse<>(ErrorCode.OK.code(), "文章已删除", null);
    }
}
