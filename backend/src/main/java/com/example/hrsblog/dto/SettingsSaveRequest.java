package com.example.hrsblog.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 站点配置整体保存请求：{@code settings} 是 key → 原始值的扁平 map，
 * 与 admin 设置页的 registry 扁平结构一致。
 */
public record SettingsSaveRequest(
        @NotNull(message = "settings 不能为空")
        Map<String, Object> settings) {
}
