package org.lpu.dev.codes.helpdesk.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** Comma-separated domains, e.g. lpulaguna.edu.ph,lpusc.edu.ph */
    private String allowedEmailDomain = "lpulaguna.edu.ph,lpusc.edu.ph";

    public String getAllowedEmailDomain() {
        return allowedEmailDomain;
    }

    public void setAllowedEmailDomain(String allowedEmailDomain) {
        this.allowedEmailDomain = allowedEmailDomain;
    }

    public List<String> allowedEmailDomains() {
        if (allowedEmailDomain == null || allowedEmailDomain.isBlank()) {
            return List.of("lpulaguna.edu.ph");
        }
        return Arrays.stream(allowedEmailDomain.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(domain -> !domain.isEmpty())
                .distinct()
                .toList();
    }

    public String primaryEmailDomain() {
        List<String> domains = allowedEmailDomains();
        return domains.isEmpty() ? "lpulaguna.edu.ph" : domains.getFirst();
    }

    public boolean isAllowedEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        for (String domain : allowedEmailDomains()) {
            if (normalized.endsWith("@" + domain)) {
                return true;
            }
        }
        return false;
    }

    public String allowedDomainsDisplay() {
        return String.join(" or ", allowedEmailDomains().stream().map(domain -> "@" + domain).toList());
    }
}
