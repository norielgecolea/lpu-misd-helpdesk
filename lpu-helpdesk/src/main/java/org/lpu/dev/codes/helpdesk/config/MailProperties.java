package org.lpu.dev.codes.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public class MailProperties {

    private String fromAddress = "system.notifications@lpulaguna.edu.ph";
    private String fromName = "LPU Laguna MISD Helpdesk";
    /** Public helpdesk UI base (no trailing slash), e.g. https://helpdesk.lpulaguna.com */
    private String publicBaseUrl = "http://localhost";
    /** Admin portal UI base (no trailing slash), e.g. https://helpdeskadmin.lpulaguna.com */
    private String adminBaseUrl = "http://localhost";

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getAdminBaseUrl() {
        return adminBaseUrl;
    }

    public void setAdminBaseUrl(String adminBaseUrl) {
        this.adminBaseUrl = adminBaseUrl;
    }

    public String trimmedPublicBaseUrl() {
        return trimTrailingSlash(publicBaseUrl != null ? publicBaseUrl : "http://localhost");
    }

    public String trimmedAdminBaseUrl() {
        return trimTrailingSlash(adminBaseUrl != null ? adminBaseUrl : "http://localhost");
    }

    private static String trimTrailingSlash(String value) {
        String v = value.trim();
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }
}
