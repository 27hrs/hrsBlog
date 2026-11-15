package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.dto.SettingValuesResponse;
import com.example.hrsblog.dto.SettingsSaveRequest;
import com.example.hrsblog.service.SiteSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员站点配置接口（需要 ROLE_ADMIN）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/settings}：读取全部配置（含私密项），值为已按类型反序列化的 map；</li>
 *   <li>{@code PUT /api/admin/settings}：整体替换保存，body 为 {@code {settings: {key: value}}}；
 *       未知 key 或值不合法返回 400，data 中带字段级错误（与 admin 前端 fieldErrors 处理兼容）。</li>
 * </ul>
 *
 * <p>配置元数据（分组/类型/公开性/排序）由服务端 {@code SiteSettingCatalog} 决定，
 * 前端提交的任何分组或公开性标记都会被忽略。
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingController {

    private final SiteSettingService siteSettingService;

    @GetMapping
    public ApiResponse<SettingValuesResponse> get() {
        return ApiResponse.ok(siteSettingService.getAll());
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> replaceAll(@Valid @RequestBody SettingsSaveRequest request) {
        Map<String, String> errors = siteSettingService.validate(request.settings());
        if (!errors.isEmpty()) {
            String message = errors.values().iterator().next();
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, message, errors));
        }
        siteSettingService.replaceAll(request.settings());
        // 成功时 data 为 null，但泛型需与 400 分支（字段错误 map）统一
        return ResponseEntity.ok(ApiResponse.ok((Map<String, String>) null));
    }
}
