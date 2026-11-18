package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.dto.ShuoshuoAdminResponse;
import com.example.hrsblog.dto.ShuoshuoCreateRequest;
import com.example.hrsblog.dto.ShuoshuoQuery;
import com.example.hrsblog.dto.ShuoshuoUpdateRequest;
import com.example.hrsblog.service.ShuoshuoService;
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
 * 管理员说说接口，需要 ROLE_ADMIN（由 SecurityConfig 的 /api/admin/** 统一校验）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/shuoshuo}：分页列表（草稿 + 已发布，支持 visibility 筛选）；</li>
 *   <li>{@code GET /api/admin/shuoshuo/{id}}：详情（编辑表单取数）；</li>
 *   <li>{@code POST /api/admin/shuoshuo}：新建（落成私有草稿）；</li>
 *   <li>{@code PUT /api/admin/shuoshuo/{id}}：更新正文；</li>
 *   <li>{@code POST /api/admin/shuoshuo/{id}/publish}：发布（首次发布回填发布时间）；</li>
 *   <li>{@code POST /api/admin/shuoshuo/{id}/unpublish}：撤回（保留发布时间）；</li>
 *   <li>{@code DELETE /api/admin/shuoshuo/{id}}：删除。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/shuoshuo")
@RequiredArgsConstructor
public class AdminShuoshuoController {

    private final ShuoshuoService shuoshuoService;

    @GetMapping
    public ApiResponse<PageResponse<ShuoshuoAdminResponse>> list(@Valid @ModelAttribute ShuoshuoQuery query) {
        return ApiResponse.ok(shuoshuoService.listForAdmin(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<ShuoshuoAdminResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(shuoshuoService.getForAdmin(id));
    }

    /** 新建说说：固定落成私有草稿，需要显式调用发布接口才会对外可见。 */
    @PostMapping
    public ResponseEntity<ApiResponse<ShuoshuoAdminResponse>> create(
            @Valid @RequestBody ShuoshuoCreateRequest request) {
        ShuoshuoAdminResponse created = shuoshuoService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<ShuoshuoAdminResponse> update(@PathVariable("id") Long id,
            @Valid @RequestBody ShuoshuoUpdateRequest request) {
        return ApiResponse.ok(shuoshuoService.update(id, request));
    }

    @PostMapping("/{id}/publish")
    public ApiResponse<ShuoshuoAdminResponse> publish(@PathVariable("id") Long id) {
        return ApiResponse.ok(shuoshuoService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    public ApiResponse<ShuoshuoAdminResponse> unpublish(@PathVariable("id") Long id) {
        return ApiResponse.ok(shuoshuoService.unpublish(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        shuoshuoService.delete(id);
        return new ApiResponse<>(ErrorCode.OK.code(), "说说已删除", null);
    }
}
