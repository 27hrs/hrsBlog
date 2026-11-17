package com.example.hrsblog.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import com.example.hrsblog.repository.SiteSettingRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 站点配置校验单元测试：本次新增字段（icpUrl 链接协议、footerStartYear 年份范围、
 * aboutContent 扩展长度上限）的行为，以及既有校验不回归。
 */
@ExtendWith(MockitoExtension.class)
class SiteSettingServiceTest {

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SiteSettingService siteSettingService;

    @BeforeEach
    void setUp() {
        siteSettingService = new SiteSettingService(siteSettingRepository, new ObjectMapper(), eventPublisher);
    }

    @Test
    void icpUrlRejectsNonHttpScheme() {
        Map<String, String> errors = siteSettingService.validate(Map.of("icpUrl", "javascript:alert(1)"));

        assertThat(errors).containsKey("icpUrl");
    }

    @Test
    void icpUrlAcceptsHttps() {
        Map<String, String> errors = siteSettingService.validate(Map.of("icpUrl", "https://beian.miit.gov.cn/"));

        assertThat(errors).isEmpty();
    }

    @Test
    void footerStartYearRejectsOutOfRange() {
        Map<String, String> below = siteSettingService.validate(Map.of("footerStartYear", 1899));
        Map<String, String> above = siteSettingService.validate(Map.of("footerStartYear", 2101));

        assertThat(below).containsKey("footerStartYear");
        assertThat(above).containsKey("footerStartYear");
    }

    @Test
    void footerStartYearAcceptsValidYear() {
        Map<String, String> errors = siteSettingService.validate(Map.of("footerStartYear", 2024));

        assertThat(errors).isEmpty();
    }

    @Test
    void aboutContentAllowsUpToTwentyThousandChars() {
        String ok = "a".repeat(20000);
        String tooLong = "a".repeat(20001);

        assertThat(siteSettingService.validate(Map.of("aboutContent", ok))).isEmpty();
        assertThat(siteSettingService.validate(Map.of("aboutContent", tooLong))).containsKey("aboutContent");
    }

    @Test
    void unknownKeyIsRejected() {
        Map<String, String> errors = siteSettingService.validate(Map.of("notASetting", "x"));

        assertThat(errors).containsKey("notASetting");
    }
}
