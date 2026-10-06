package com.jobproof.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobproof.login-rate-limit")
public class LoginRateLimitProperties {
    private String mode = "redis";
    private int accountLimit = 8;
    private int ipLimit = 40;
    private long windowSeconds = 300;
    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public int getAccountLimit() { return accountLimit; }
    public void setAccountLimit(int value) { accountLimit = value; }
    public int getIpLimit() { return ipLimit; }
    public void setIpLimit(int value) { ipLimit = value; }
    public long getWindowSeconds() { return windowSeconds; }
    public void setWindowSeconds(long value) { windowSeconds = value; }
}
