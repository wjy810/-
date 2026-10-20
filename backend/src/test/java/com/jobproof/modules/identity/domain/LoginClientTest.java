package com.jobproof.modules.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginClientTest {

    @Test
    void namesTheKindOfDeviceWithoutVersions() {
        assertThat(LoginClient.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36").label())
                .isEqualTo("Windows · Chrome");
        assertThat(LoginClient.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36 Edg/141.0").label())
                .isEqualTo("Windows · Edge");
        assertThat(LoginClient.of("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15").label())
                .isEqualTo("Mac · Safari");
        assertThat(LoginClient.of("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.50").label())
                .isEqualTo("iPhone · 微信");
        assertThat(LoginClient.of("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Mobile Safari/537.36").label())
                .isEqualTo("Android · Chrome");
    }

    @Test
    void aBrowserUpdateIsNotANewDevice() {
        assertThat(LoginClient.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/139.0 Safari/537.36"))
                .isEqualTo(LoginClient.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/141.0 Safari/537.36"));
        assertThat(LoginClient.of(null)).isEqualTo(LoginClient.UNKNOWN);
        assertThat(LoginClient.of("curl/8.0").label()).isEqualTo("未知设备");
    }
}
