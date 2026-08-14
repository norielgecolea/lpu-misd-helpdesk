package org.lpu.dev.codes.helpdesk.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.GateAttendanceProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fetches gate attendance profile photos server-side so browsers on HTTPS
 * (Safari especially) never load mixed-content {@code http://} image URLs.
 */
@Service
public class GatePhotoProxyService {

    private static final Logger log = LogManager.getLogger(GatePhotoProxyService.class);

    private final GateAttendanceProperties gateAttendanceProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public GatePhotoProxyService(GateAttendanceProperties gateAttendanceProperties) {
        this.gateAttendanceProperties = gateAttendanceProperties;
    }

    public ProxiedPhoto fetch(String rawFilename) {
        String filename = sanitizeFilename(rawFilename);
        String upstream = gateAttendanceProperties.upstreamPhotoUrl(filename);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(upstream))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .header("Accept", "image/*,*/*")
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Gate photo proxy upstream status={} url={}", response.statusCode(), upstream);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to load photo from gate system");
            }
            byte[] body = response.body();
            if (body == null || body.length == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Photo not found");
            }
            return new ProxiedPhoto(body, mediaTypeFor(filename, response.headers().firstValue("Content-Type").orElse(null)));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Gate photo proxy failed url={}: {}", upstream, ex.toString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to load photo from gate system");
        }
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo filename is required");
        }
        String filename = raw.contains("/") ? raw.substring(raw.lastIndexOf('/') + 1) : raw.trim();
        if (filename.isBlank() || filename.contains("..") || !filename.matches("[A-Za-z0-9._\\- ()]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid photo filename");
        }
        return filename;
    }

    private static MediaType mediaTypeFor(String filename, String upstreamContentType) {
        if (upstreamContentType != null && !upstreamContentType.isBlank()) {
            try {
                MediaType parsed = MediaType.parseMediaType(upstreamContentType.split(";", 2)[0].trim());
                if (parsed.getType().equals("image") || parsed.equals(MediaType.APPLICATION_OCTET_STREAM)) {
                    return parsed.equals(MediaType.APPLICATION_OCTET_STREAM)
                            ? guessFromFilename(filename)
                            : parsed;
                }
            } catch (Exception ignored) {
                // fall through to filename guess
            }
        }
        return guessFromFilename(filename);
    }

    private static MediaType guessFromFilename(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    public record ProxiedPhoto(byte[] bytes, MediaType mediaType) {
    }
}
