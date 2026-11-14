package com.example.hrsblog.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置约束测试：JWT 密钥必须来自环境变量且足够长（HS256 需要 256 位），
 * 缺失或过短时应在启动阶段直接失败，而不是悄悄使用弱密钥。
 */
class JwtPropertiesValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsConfiguredSecretOfSufficientLength() {
        JwtProperties properties = new JwtProperties(
                "a-perfectly-fine-secret-for-hs256-signing-0123456789", "hrsblog", Duration.ofHours(2));

        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    void rejectsMissingSecret() {
        JwtProperties properties = new JwtProperties("", "hrsblog", Duration.ofHours(2));

        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(properties);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anySatisfy(violation ->
                assertThat(violation.getPropertyPath().toString()).isEqualTo("secret"));
    }

    @Test
    void rejectsSecretShorterThanThirtyTwoCharacters() {
        JwtProperties properties = new JwtProperties("too-short", "hrsblog", Duration.ofHours(2));

        assertThat(validator.validate(properties)).isNotEmpty();
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties(
                "a-perfectly-fine-secret-for-hs256-signing-0123456789", "hrsblog", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
