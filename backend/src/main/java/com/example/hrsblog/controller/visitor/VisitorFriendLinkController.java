package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.ClientIpResolver;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.config.FriendLinkProperties;
import com.example.hrsblog.dto.FriendLinkPublicResponse;
import com.example.hrsblog.dto.FriendLinkSubmitRequest;
import com.example.hrsblog.service.FriendLinkService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 访客友链接口（匿名可访问）。
 *
 * <ul>
 *   <li>{@code GET /api/visitors/friend-links}：已通过的友链列表，响应不含联系方式；</li>
 *   <li>{@code POST /api/visitors/friend-links}：提交友链申请，按来源 IP 限频。</li>
 * </ul>
 *
 * <p>这是前台唯一需要后端在线的接口：友链内容本身仍是构建期静态同步的。
 */
@RestController
@RequestMapping("/api/visitors/friend-links")
@RequiredArgsConstructor
public class VisitorFriendLinkController {

    private final FriendLinkService friendLinkService;
    private final FriendLinkProperties friendLinkProperties;

    @GetMapping
    public ApiResponse<List<FriendLinkPublicResponse>> list() {
        return ApiResponse.ok(friendLinkService.listApproved());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FriendLinkPublicResponse>> submit(
            @Valid @RequestBody FriendLinkSubmitRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = ClientIpResolver.resolve(httpRequest, friendLinkProperties.trustForwardedHeaders());
        FriendLinkPublicResponse created = friendLinkService.submit(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(ErrorCode.OK.code(), "友链申请已提交，等待站长审核", created));
    }
}
