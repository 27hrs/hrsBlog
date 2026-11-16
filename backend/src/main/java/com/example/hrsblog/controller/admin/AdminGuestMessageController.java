package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.dto.GuestMessageAdminResponse;
import com.example.hrsblog.dto.GuestMessageQuery;
import com.example.hrsblog.dto.GuestMessageReplyRequest;
import com.example.hrsblog.dto.GuestMessageReviewRequest;
import com.example.hrsblog.service.GuestMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
 * 管理员留言接口，需要 ROLE_ADMIN（由 SecurityConfig 的 /api/admin/** 统一校验）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/guest-messages}：分页列表（支持 status 筛选）；</li>
 *   <li>{@code GET /api/admin/guest-messages/{id}}：详情；</li>
 *   <li>{@code POST /api/admin/guest-messages/{id}/review}：审核通过 / 拒绝；</li>
 *   <li>{@code PUT /api/admin/guest-messages/{id}/reply}：回复（一条留言一条回复，再次提交即覆盖）；</li>
 *   <li>{@code DELETE /api/admin/guest-messages/{id}}：删除（先删回复再删留言）。</li>
 * </ul>
 *
 * <p>只有这里的响应包含邮箱、来源 IP 与 UA。
 */
@RestController
@RequestMapping("/api/admin/guest-messages")
@RequiredArgsConstructor
public class AdminGuestMessageController {

    private final GuestMessageService guestMessageService;

    @GetMapping
    public ApiResponse<PageResponse<GuestMessageAdminResponse>> list(@Valid @ModelAttribute GuestMessageQuery query) {
        return ApiResponse.ok(guestMessageService.listForAdmin(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<GuestMessageAdminResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(guestMessageService.getForAdmin(id));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<GuestMessageAdminResponse> review(@PathVariable("id") Long id,
            @Valid @RequestBody GuestMessageReviewRequest request) {
        return ApiResponse.ok(guestMessageService.review(id, request));
    }

    @PutMapping("/{id}/reply")
    public ApiResponse<GuestMessageAdminResponse> reply(@PathVariable("id") Long id,
            @Valid @RequestBody GuestMessageReplyRequest request,
            Authentication authentication) {
        return ApiResponse.ok(guestMessageService.reply(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        guestMessageService.delete(id);
        return new ApiResponse<>(ErrorCode.OK.code(), "留言已删除", null);
    }
}
