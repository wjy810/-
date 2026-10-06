package com.jobproof.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jobproof.proxy")
public class TrustedProxyProperties {
    private List<String> trustedProxies = List.of();
    public List<String> getTrustedProxies() { return trustedProxies; }
    public void setTrustedProxies(List<String> value) { trustedProxies = value == null ? List.of() : value; }
}
