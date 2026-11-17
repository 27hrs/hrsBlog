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
    void footerSettingsArePublicInFooterGroup() {
        assertThat(SiteSettingCatalog.get("footerStartYear")).satisfies(spec -> {
            assertThat(spec.group()).isEqualTo("footer");
            assertThat(spec.valueType()).isEqualTo(ValueType.NUMBER);
            assertThat(spec.publicSetting()).isTrue();
            assertThat(spec.min()).isEqualTo(1900d);
            assertThat(spec.max()).isEqualTo(2100d);
        });
        assertThat(SiteSettingCatalog.get("footerNote")).satisfies(spec -> {
            assertThat(spec.group()).isEqualTo("footer");
            assertThat(spec.valueType()).isEqualTo(ValueType.STRING);
            assertThat(spec.max()).isEqualTo(100d);
        });
        assertThat(SiteSettingCatalog.get("icp")).satisfies(spec -> {
            assertThat(spec.group()).isEqualTo("footer");
            assertThat(spec.publicSetting()).isTrue();
        });
        assertThat(SiteSettingCatalog.get("icpUrl")).satisfies(spec -> {
            assertThat(spec.group()).isEqualTo("footer");
            assertThat(spec.publicSetting()).isTrue();
        });
        assertThat(SiteSettingCatalog.get("footerSyncedAt")).satisfies(spec -> {
            assertThat(spec.group()).isEqualTo("footer");
            assertThat(spec.max()).isEqualTo(40d);
        });
    }

    @Test
    void authorAndSiteNewKeysMatchConfigShape() {
        assertThat(SiteSettingCatalog.get("authorTagline")).satisfies(spec -> {
            assertThat(spec.group()).isEqualTo("identity");
            assertThat(spec.publicSetting()).isTrue();
            assertThat(spec.max()).isEqualTo(80d);
        });
        assertThat(SiteSettingCatalog.get("authorIntro")).satisfies(spec -> {
            assertThat(spec.group()).isEqualTo("identity");
            assertThat(spec.max()).isEqualTo(500d);
        });
        assertThat(SiteSettingCatalog.get("siteWordmark")).satisfies(spec -> {
            assertThat(spec.group()).isEqualTo("basic");
            assertThat(spec.publicSetting()).isTrue();
            assertThat(spec.max()).isEqualTo(60d);
        });
    }

    @Test
    void aboutContentIsPublicStringWithExtendedLimit() {
        SiteSettingCatalog.FieldSpec spec = SiteSettingCatalog.get("aboutContent");

        assertThat(spec).isNotNull();
        assertThat(spec.group()).isEqualTo("about");
        assertThat(spec.valueType()).isEqualTo(ValueType.STRING);
        assertThat(spec.publicSetting()).isTrue();
        assertThat(spec.max()).isEqualTo(20000d);
    }

    @Test
    void allowIndexingIsPublicInBasicGroup() {
        SiteSettingCatalog.FieldSpec spec = SiteSettingCatalog.get("allowIndexing");

        assertThat(spec).isNotNull();
        assertThat(spec.group()).isEqualTo("basic");
        assertThat(spec.valueType()).isEqualTo(ValueType.BOOLEAN);
        assertThat(spec.publicSetting()).isTrue();
    }

    @Test
    void redundantSettingsAreGone() {
        assertThat(SiteSettingCatalog.contains("footerText")).isFalse();
        assertThat(SiteSettingCatalog.contains("robots")).isFalse();
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
