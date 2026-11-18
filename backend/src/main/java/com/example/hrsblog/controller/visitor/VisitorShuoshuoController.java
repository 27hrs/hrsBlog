package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.dto.ShuoshuoPublicResponse;
import com.example.hrsblog.dto.ShuoshuoQuery;
import com.example.hrsblog.service.ShuoshuoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访客说说接口（匿名可访问）。
 *
 * <ul>
 *   <li>{@code GET /api/visitors/shuoshuo}：分页列表，仅返回 PUBLIC，按发布时间倒序。</li>
 * </ul>
 *
 * <p>响应体只含正文与发布时间：草稿与编辑痕迹都不会外泄。
 */
@RestController
@RequestMapping("/api/visitors/shuoshuo")
@RequiredArgsConstructor
public class VisitorShuoshuoController {

    private final ShuoshuoService shuoshuoService;

    @GetMapping
    public ApiResponse<PageResponse<ShuoshuoPublicResponse>> list(@Valid @ModelAttribute ShuoshuoQuery query) {
        return ApiResponse.ok(shuoshuoService.listPublic(query));
    }
}
