package org.lpu.dev.codes.helpdesk.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Signs gate picture paths the same way gate attendance does.
 * The key is the gate JWT secret text, not the helpdesk login secret.
 */
@Component
public class GateMediaSigner {

    private static final Logger log = LogManager.getLogger(GateMediaSigner.class);
    static final long MAX_AGE_SECONDS = 30L * 24 * 60 * 60;

    private final byte[] key;

    public GateMediaSigner(GateAttendanceProperties properties) {
        String secret = resolveSecret(properties);
        this.key = secret.isBlank() ? null : secret.getBytes(StandardCharsets.UTF_8);
        if (this.key == null) {
            log.warn("Gate photo signing key is missing. Picture downloads from gate attendance will be rejected.");
        }
    }

    /** Appends {@code exp} and {@code sig} for {@code /pictures/{filename}}. */
    public String signUpstreamUrl(String filename, String unsignedUrl) {
        if (key == null || filename == null || filename.isBlank() || unsignedUrl == null || unsignedUrl.isBlank()) {
            return unsignedUrl;
        }
        String path = "/pictures/" + filename;
        long exp = Instant.now().getEpochSecond() + MAX_AGE_SECONDS;
        return unsignedUrl + "?exp=" + exp + "&sig=" + mac(path, exp);
    }

    private String mac(String path, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] raw = mac.doFinal((path + "\n" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign gate photo URL", ex);
        }
    }

    private static String resolveSecret(GateAttendanceProperties properties) {
        String configured = properties.getMediaSecret();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String file = properties.getMediaSecretFile();
        if (file == null || file.isBlank()) {
            return "";
        }
        try {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path)) {
                return "";
            }
            return Files.readString(path).trim();
        } catch (Exception ex) {
            log.warn("Unable to read gate photo signing key from {}: {}", file, ex.toString());
            return "";
        }
    }
}
