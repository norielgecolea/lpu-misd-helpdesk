package org.lpu.dev.codes.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.msal")
public class MsalProperties {

    private String tenantId;
    private String clientId;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public boolean isConfigured() {
        return tenantId != null && !tenantId.isBlank() && clientId != null && !clientId.isBlank();
    }
}
