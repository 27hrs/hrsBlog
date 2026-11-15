package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.dto.SettingValuesResponse;
import com.example.hrsblog.service.SiteSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访客站点配置接口（匿名可访问）。
 *
 * <p>{@code GET /api/visitors/settings}：仅返回 {@code is_public=true} 的配置键值，
 * 供博客前台构建期消费（背景图、基本设置、作者资料、友链、SEO 等）。
 * 集成与高级等私密配置不会出现在响应中。
 */
@RestController
@RequestMapping("/api/visitors/settings")
@RequiredArgsConstructor
public class VisitorSettingController {

    private final SiteSettingService siteSettingService;

    @GetMapping
    public ApiResponse<SettingValuesResponse> getPublic() {
        return ApiResponse.ok(siteSettingService.getPublic());
    }
}
