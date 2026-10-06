package com.jobproof.modules.aigateway.security;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix="jobproof.ai.gateway")
public class MasterKeyProperties { private String masterKey=""; private boolean enabled; public String getMasterKey(){return masterKey;} public void setMasterKey(String v){masterKey=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} }