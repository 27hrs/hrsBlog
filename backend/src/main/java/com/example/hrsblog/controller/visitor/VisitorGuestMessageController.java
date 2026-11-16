package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.ClientIpResolver;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.config.GuestMessageProperties;
import com.example.hrsblog.dto.GuestMessagePublicResponse;
import com.example.hrsblog.dto.GuestMessageSubmitRequest;
import com.example.hrsblog.service.GuestMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访客留言接口（匿名可访问）。
 *
 * <ul>
 *   <li>{@code GET /api/visitors/guest-messages}：已通过的留言分页列表，响应不含邮箱与来源信息；</li>
 *   <li>{@code POST /api/visitors/guest-messages}：提交留言，按来源 IP 限频。</li>
 * </ul>
 *
 * <p>留言走运行时接口而不是构建期同步：审核通过后立即可见，不必等 CI 重建。
 */
@RestController
@RequestMapping("/api/visitors/guest-messages")
@RequiredArgsConstructor
@Validated
public class VisitorGuestMessageController {

    private final GuestMessageService guestMessageService;
    private final GuestMessageProperties guestMessageProperties;

    @GetMapping
    public ApiResponse<PageResponse<GuestMessagePublicResponse>> list(
            @RequestParam(value = "page", defaultValue = "0") @Min(value = 0, message = "page 不能小于 0") int page,
            @RequestParam(value = "size", required = false)
            @Min(value = 1, message = "size 不能小于 1")
            @Max(value = 50, message = "size 不能大于 50") Integer size) {
        return ApiResponse.ok(guestMessageService.listApproved(page, size));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GuestMessagePublicResponse>> submit(
            @Valid @RequestBody GuestMessageSubmitRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = ClientIpResolver.resolve(httpRequest, guestMessageProperties.trustForwardedHeaders());
        String userAgent = httpRequest.getHeader("User-Agent");
        GuestMessagePublicResponse created = guestMessageService.submit(request, clientIp, userAgent);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(ErrorCode.OK.code(), "留言已提交，等待站长审核", created));
    }
}
