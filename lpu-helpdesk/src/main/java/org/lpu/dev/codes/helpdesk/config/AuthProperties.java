package org.lpu.dev.codes.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String allowedEmailDomain = "lpulaguna.edu.ph";

    public String getAllowedEmailDomain() {
        return allowedEmailDomain;
    }

    public void setAllowedEmailDomain(String allowedEmailDomain) {
        this.allowedEmailDomain = allowedEmailDomain;
    }
}
