package com.example.hrsblog.service;

import com.example.hrsblog.domain.SiteSetting;
import com.example.hrsblog.domain.SiteSetting.ValueType;
import com.example.hrsblog.dto.SettingValuesResponse;
import com.example.hrsblog.repository.SiteSettingRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点配置业务：基于 {@code site_setting} 表的 key/value 持久化。
 *
 * <p>约定：
 * <ul>
 *   <li>配置元数据（分组、类型、公开性、排序）由 {@link SiteSettingCatalog} 在服务端
 *       决定，前端只提交 key/value 扁平 map；</li>
 *   <li>保存采用「整体替换」语义：事务内 upsert 本次提交的键、删除未提交的旧键，
 *       保证表内状态与提交内容完全一致；</li>
 *   <li>访客接口只输出 {@code is_public=true} 的键，私密配置（集成、高级）永不外泄。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteSettingService {

    private static final int MAX_STRING_LENGTH = 5000;
    private static final int MAX_JSON_LENGTH = 20000;

    private final SiteSettingRepository siteSettingRepository;
    private final ObjectMapper objectMapper;

    /** 读取全部配置（管理端）：按 value_type 反序列化为原生类型。 */
    @Transactional(readOnly = true)
    public SettingValuesResponse getAll() {
        return new SettingValuesResponse(toValueMap(siteSettingRepository.findAllByOrderByConfigGroupAscSortOrderAsc()));
    }

    /** 读取公开配置（访客端/博客构建）：仅 is_public=true。 */
    @Transactional(readOnly = true)
    public SettingValuesResponse getPublic() {
        return new SettingValuesResponse(toValueMap(siteSettingRepository.findByPublicSettingTrueOrderBySortOrderAsc()));
    }

    /**
     * 校验一次整体提交：返回 key → 错误信息的 map；为空表示全部合法。
     * 只校验不落库，控制器据此返回 400 + 字段级错误。
     */
    public Map<String, String> validate(Map<String, Object> settings) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String key = entry.getKey();
            if (!SiteSettingCatalog.contains(key)) {
                errors.put(key, "未知的配置项");
                continue;
            }
            String error = validateValue(SiteSettingCatalog.get(key), entry.getValue());
            if (error != null) {
                errors.put(key, error);
            }
        }
        return errors;
    }

    /** 整体替换保存：必须在 {@link #validate(Map)} 通过后调用。 */
    @Transactional
    public void replaceAll(Map<String, Object> settings) {
        List<SiteSetting> existing = siteSettingRepository.findAllByOrderByConfigGroupAscSortOrderAsc();
        Map<String, SiteSetting> existingByKey = new LinkedHashMap<>();
        existing.forEach(row -> existingByKey.put(row.getConfigKey(), row));

        List<SiteSetting> toSave = new ArrayList<>();
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            SiteSettingCatalog.FieldSpec spec = SiteSettingCatalog.get(entry.getKey());
            if (spec == null) {
                continue; // validate 已拦截，防御性跳过
            }
            SiteSetting row = existingByKey.remove(entry.getKey());
            if (row == null) {
                row = new SiteSetting();
                row.setConfigGroup(spec.group());
                row.setConfigKey(entry.getKey());
            }
            row.setConfigValue(serialize(spec, entry.getValue()));
            row.setValueType(spec.valueType());
            row.setDescription(spec.description());
            row.setPublicSetting(spec.publicSetting());
            row.setSortOrder(spec.sortOrder());
            toSave.add(row);
        }

        // 本次未提交的旧键一律删除，保持“整体替换”语义
        if (!existingByKey.isEmpty()) {
            siteSettingRepository.deleteAllInBatch(existingByKey.values());
        }
        siteSettingRepository.saveAll(toSave);
        log.info("站点配置已保存 keys={}", toSave.stream().map(SiteSetting::getConfigKey).toList());
    }

    // ── 校验 ──────────────────────────────────────────────

    private String validateValue(SiteSettingCatalog.FieldSpec spec, Object value) {
        if (value == null) {
            return null; // null 视为清空
        }
        switch (spec.valueType()) {
            case STRING -> {
                String text = String.valueOf(value);
                if (text.length() > MAX_STRING_LENGTH) {
                    return "内容长度超出限制（最多 " + MAX_STRING_LENGTH + " 字符）";
                }
                if (spec.max() != null && text.length() > spec.max().intValue()) {
                    return "长度不能超过 " + spec.max().intValue() + " 个字符";
                }
                if (spec.isImage() && !text.isBlank() && !isSafeResourceUrl(text)) {
                    return "图片链接必须以 http(s):// 或 / 开头";
                }
                if (isHttpUrlField(spec.key()) && !text.isBlank() && !startsWithHttp(text)) {
                    return "链接必须以 http(s):// 开头";
                }
            }
            case NUMBER -> {
                Double number = toNumber(value);
                if (number == null) {
                    return "必须是数字";
                }
                if (spec.min() != null && number < spec.min()) {
                    return "不能小于 " + spec.min().intValue();
                }
                if (spec.max() != null && number > spec.max()) {
                    return "不能大于 " + spec.max().intValue();
                }
            }
            case BOOLEAN -> {
                if (!isBoolean(value)) {
                    return "必须是布尔值";
                }
            }
            case JSON -> {
                if (!(value instanceof List<?> || value instanceof Map<?, ?>)) {
                    return "必须是数组或对象";
                }
                String serialized = serializeJson(value);
                if (serialized == null || serialized.length() > MAX_JSON_LENGTH) {
                    return "内容长度超出限制";
                }
                if ("friendLinks".equals(spec.key())) {
                    String linkError = validateFriendLinks(value);
                    if (linkError != null) {
                        return linkError;
                    }
                }
            }
        }
        return null;
    }

    /** 友链列表项约束：name/url 必填，url 必须是 http(s) 链接。 */
    private String validateFriendLinks(Object value) {
        if (!(value instanceof List<?> items)) {
            return null;
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                return "第 " + (i + 1) + " 项必须是对象";
            }
            Object name = map.get("name");
            Object url = map.get("url");
            if (name == null || String.valueOf(name).isBlank()) {
                return "第 " + (i + 1) + " 项缺少名称";
            }
            if (url == null || String.valueOf(url).isBlank() || !startsWithHttp(String.valueOf(url))) {
                return "第 " + (i + 1) + " 项链接必须以 http(s):// 开头";
            }
            if (String.valueOf(name).length() > 50) {
                return "第 " + (i + 1) + " 项名称过长";
            }
        }
        return null;
    }

    private boolean isHttpUrlField(String key) {
        return "githubUrl".equals(key) || "xUrl".equals(key)
                || "analyticsScriptUrl".equals(key) || "webhookUrl".equals(key);
    }

    private boolean isSafeResourceUrl(String url) {
        return startsWithHttp(url) || url.startsWith("/");
    }

    private boolean startsWithHttp(String url) {
        String lower = url.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isBoolean(Object value) {
        return value instanceof Boolean || "true".equalsIgnoreCase(String.valueOf(value))
                || "false".equalsIgnoreCase(String.valueOf(value));
    }

    private Double toNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ── 序列化 ──────────────────────────────────────────────

    private String serialize(SiteSettingCatalog.FieldSpec spec, Object value) {
        if (value == null) {
            return "";
        }
        return switch (spec.valueType()) {
            case STRING -> String.valueOf(value);
            case NUMBER -> {
                Double number = toNumber(value);
                yield number == null ? "" : stripTrailingZero(number);
            }
            case BOOLEAN -> String.valueOf(isBoolean(value) && Boolean.parseBoolean(String.valueOf(value)));
            case JSON -> serializeJson(value) == null ? "" : serializeJson(value);
        };
    }

    private String serializeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException ex) {
            log.warn("配置 JSON 序列化失败: {}", ex.getMessage());
            return null;
        }
    }

    private String stripTrailingZero(Double number) {
        if (number == Math.floor(number) && !number.isInfinite()) {
            return String.valueOf(number.longValue());
        }
        return String.valueOf(number);
    }

    // ── 反序列化 ──────────────────────────────────────────────

    private Map<String, Object> toValueMap(List<SiteSetting> rows) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (SiteSetting row : rows) {
            values.put(row.getConfigKey(), deserialize(row));
        }
        return values;
    }

    private Object deserialize(SiteSetting row) {
        String raw = row.getConfigValue();
        ValueType valueType = row.getValueType();
        if (valueType == ValueType.STRING) {
            return raw == null ? "" : raw;
        }
        if (raw == null || raw.isBlank()) {
            return valueType == ValueType.JSON ? List.of() : defaultValue(valueType);
        }
        return switch (valueType) {
            case NUMBER -> {
                Double number = toNumber(raw);
                yield number == null ? defaultValue(valueType) : number;
            }
            case BOOLEAN -> Boolean.parseBoolean(raw);
            case JSON -> parseJson(raw);
            default -> raw;
        };
    }

    private Object defaultValue(ValueType valueType) {
        return switch (valueType) {
            case NUMBER -> 0;
            case BOOLEAN -> false;
            case JSON -> List.of();
            default -> "";
        };
    }

    private Object parseJson(String raw) {
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (JacksonException ex) {
            log.warn("配置 JSON 反序列化失败: {}", ex.getMessage());
            return List.of();
        }
    }
}
