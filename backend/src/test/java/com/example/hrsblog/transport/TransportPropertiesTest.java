package com.example.hrsblog.transport;

import com.example.hrsblog.config.TransportProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TransportProperties} 的取值语义：空=禁用，v1/v2=启用，其他值启动即失败。
 */
class TransportPropertiesTest {

    @Test
    void blankValueMeansDisabled() {
        assertThat(new TransportProperties("").proxyProtocolEnabled()).isFalse();
        assertThat(new TransportProperties("   ").proxyProtocolEnabled()).isFalse();
        assertThat(new TransportProperties(null).proxyProtocolEnabled()).isFalse();
    }

    @Test
    void v1AndV2AreEnabled() {
        assertThat(new TransportProperties("v1").proxyProtocolEnabled()).isTrue();
        assertThat(new TransportProperties("v2").proxyProtocolEnabled()).isTrue();
        assertThat(new TransportProperties("V2").proxyProtocolEnabled()).isTrue();
    }

    @Test
    void unknownVersionFailsFast() {
        assertThatThrownBy(() -> new TransportProperties("v3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("v1");
    }
}
