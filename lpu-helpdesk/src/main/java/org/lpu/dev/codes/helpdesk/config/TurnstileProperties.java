package org.lpu.dev.codes.helpdesk.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.turnstile")
public class TurnstileProperties {

    private String secret = "";
    /** Comma-separated frontend hostnames approved for Siteverify. */
    private String hostnames = "";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getHostnames() {
        return hostnames;
    }

    public void setHostnames(String hostnames) {
        this.hostnames = hostnames;
    }

    public Set<String> hostnameSet() {
        if (hostnames == null || hostnames.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(hostnames.split(","))
                .map(String::trim)
                .filter(hostname -> !hostname.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
