package com.jobproof.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobproof.dev.seeker")
public class DevSeekerProperties {

    private boolean enabled;
    private String alias = "seeker";
    private String email = "seeker@jobproof.local";
    private String password = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean matchesAlias(String rawLogin) {
        return enabled && rawLogin != null && alias != null
                && alias.trim().equalsIgnoreCase(rawLogin.trim());
    }
}
