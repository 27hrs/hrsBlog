package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.dto.FriendLinkAdminResponse;
import com.example.hrsblog.dto.FriendLinkAdminUpdateRequest;
import com.example.hrsblog.dto.FriendLinkQuery;
import com.example.hrsblog.dto.FriendLinkReviewRequest;
import com.example.hrsblog.service.FriendLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 管理员友链接口，需要 ROLE_ADMIN（由 SecurityConfig 的 /api/admin/** 统一校验）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/friend-links}：分页列表（支持 status 筛选）；</li>
 *   <li>{@code GET /api/admin/friend-links/{id}}：详情（编辑表单取数）；</li>
 *   <li>{@code PUT /api/admin/friend-links/{id}}：全量更新；</li>
 *   <li>{@code POST /api/admin/friend-links/{id}/review}：审核通过 / 拒绝；</li>
 *   <li>{@code DELETE /api/admin/friend-links/{id}}：删除。</li>
 * </ul>
 *
 * <p>只有这里的响应包含联系方式（{@code contact}）与提交来源 IP。
 */
@RestController
@RequestMapping("/api/admin/friend-links")
@RequiredArgsConstructor
public class AdminFriendLinkController {

    private final FriendLinkService friendLinkService;

    @GetMapping
    public ApiResponse<PageResponse<FriendLinkAdminResponse>> list(@Valid @ModelAttribute FriendLinkQuery query) {
        return ApiResponse.ok(friendLinkService.listForAdmin(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<FriendLinkAdminResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(friendLinkService.getForAdmin(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FriendLinkAdminResponse> update(@PathVariable("id") Long id,
            @Valid @RequestBody FriendLinkAdminUpdateRequest request) {
        return ApiResponse.ok(friendLinkService.update(id, request));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<FriendLinkAdminResponse> review(@PathVariable("id") Long id,
            @Valid @RequestBody FriendLinkReviewRequest request) {
        return ApiResponse.ok(friendLinkService.review(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        friendLinkService.delete(id);
        return new ApiResponse<>(ErrorCode.OK.code(), "友链已删除", null);
    }
}
