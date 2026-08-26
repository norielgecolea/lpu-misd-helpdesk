package org.lpu.dev.codes.helpdesk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.TurnstileProperties;
import org.lpu.dev.codes.helpdesk.dto.TurnstileSiteverifyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TurnstileVerificationService {

    private static final Logger log = LogManager.getLogger(TurnstileVerificationService.class);
    private static final URI SITEVERIFY = URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");
    private static final int MAX_TOKEN_LENGTH = 2048;

    private final TurnstileProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public TurnstileVerificationService(TurnstileProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void verify(String token, String expectedAction, HttpServletRequest request) {
        String secret = properties.getSecret() == null ? "" : properties.getSecret();
        Set<String> expectedHostnames = properties.hostnameSet();
        if (
                token == null
                        || token.isEmpty()
                        || token.length() > MAX_TOKEN_LENGTH
                        || secret.isBlank()
                        || expectedHostnames.isEmpty()
        ) {
            throw forbidden();
        }

        TurnstileSiteverifyResponse result;
        try {
            String body = "secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                    + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "&remoteip=" + URLEncoder.encode(clientIp(request), StandardCharsets.UTF_8);
            HttpRequest httpRequest = HttpRequest.newBuilder(SITEVERIFY)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Turnstile siteverify HTTP {}", response.statusCode());
                throw forbidden();
            }
            result = objectMapper.readValue(response.body(), TurnstileSiteverifyResponse.class);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Turnstile siteverify failed: {}", ex.toString());
            throw forbidden();
        }

        if (
                result == null
                        || !result.success()
                        || result.action() == null
                        || !result.action().equals(expectedAction)
                        || result.hostname() == null
                        || expectedHostnames.stream().noneMatch(hostname -> hostname.equalsIgnoreCase(result.hostname()))
        ) {
            throw forbidden();
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "" : remote;
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
    }
}
