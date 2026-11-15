package com.example.hrsblog.dto;

import java.util.Map;

/**
 * 站点配置读取响应：{@code values} 是 key → 已按 value_type 反序列化的配置值。
 *
 * <p>admin 前端的 {@code extractSettingsPayload} 优先识别 {@code values} 字段；
 * blog 构建脚本直接遍历该 map。
 */
public record SettingValuesResponse(Map<String, Object> values) {
}
