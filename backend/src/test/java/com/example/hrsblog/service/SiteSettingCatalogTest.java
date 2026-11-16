package com.example.hrsblog.service;

import com.example.hrsblog.domain.SiteSetting.ValueType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 站点配置目录单元测试：留言板相关配置的元数据，以及被移除的评论配置不再存在。
 */
class SiteSettingCatalogTest {

    @Test
    void guestbookSwitchIsPublicBoolean() {
        SiteSettingCatalog.FieldSpec spec = SiteSettingCatalog.get("enableGuestbook");

        assertThat(spec).isNotNull();
        assertThat(spec.group()).isEqualTo("reading");
        assertThat(spec.valueType()).isEqualTo(ValueType.BOOLEAN);
        assertThat(spec.publicSetting()).isTrue();
    }

    @Test
    void guestbookPageSizeIsPublicNumberInRange() {
        SiteSettingCatalog.FieldSpec spec = SiteSettingCatalog.get("guestbookPageSize");

        assertThat(spec).isNotNull();
        assertThat(spec.group()).isEqualTo("reading");
        assertThat(spec.valueType()).isEqualTo(ValueType.NUMBER);
        assertThat(spec.publicSetting()).isTrue();
        assertThat(spec.min()).isEqualTo(1d);
        assertThat(spec.max()).isEqualTo(50d);
    }

    @Test
    void removedCommentSettingsAreGone() {
        assertThat(SiteSettingCatalog.contains("enableComments")).isFalse();
        assertThat(SiteSettingCatalog.contains("commentModeration")).isFalse();
    }

    @Test
    void everySpecHasUniqueSortOrder() {
        long distinctOrders = SiteSettingCatalog.all().values().stream()
                .map(SiteSettingCatalog.FieldSpec::sortOrder)
                .distinct()
                .count();

        assertThat(distinctOrders).isEqualTo(SiteSettingCatalog.all().size());
    }
}
