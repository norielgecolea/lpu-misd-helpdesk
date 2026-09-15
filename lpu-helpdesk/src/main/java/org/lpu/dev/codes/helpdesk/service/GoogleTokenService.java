package org.lpu.dev.codes.helpdesk.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.GoogleProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates Google ID tokens from GIS / OpenID Connect: RS256 against Google's
 * JWKS, then issuer, audience, expiry, and a verified email claim.
 */
@Service
public class GoogleTokenService {

    private static final Logger log = LogManager.getLogger(GoogleTokenService.class);
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final GoogleProperties googleProperties;

    private volatile ConfigurableJWTProcessor<SecurityContext> cachedProcessor;

    public GoogleTokenService(GoogleProperties googleProperties) {
        this.googleProperties = googleProperties;
    }

    public record GoogleIdentity(String email, String name) {
    }

    public boolean isConfigured() {
        return googleProperties.isConfigured();
    }

    public String clientId() {
        return googleProperties.getClientId();
    }

    public GoogleIdentity validate(String idToken, String expectedNonce) {
        if (!googleProperties.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not configured on the server yet"
            );
        }

        try {
            JWTClaimsSet claims = jwtProcessor().process(idToken, null);

            String issuer = claims.getIssuer();
            if (issuer == null || !ISSUERS.contains(issuer)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token issuer");
            }

            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(googleProperties.getClientId())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token audience");
            }

            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token has expired");
            }

            if (expectedNonce != null && !expectedNonce.isBlank()) {
                String nonce = claims.getStringClaim("nonce");
                if (!expectedNonce.equals(nonce)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token nonce");
                }
            }

            if (!isEmailVerified(claims.getClaim("email_verified"))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified");
            }

            String email = claims.getStringClaim("email");
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token did not include an email claim");
            }
            String name = claims.getStringClaim("name");

            return new GoogleIdentity(email.toLowerCase(), name);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Google ID token validation failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to validate Google sign-in token");
        }
    }

    private static boolean isEmailVerified(Object claim) {
        if (Boolean.TRUE.equals(claim)) {
            return true;
        }
        return claim != null && "true".equalsIgnoreCase(String.valueOf(claim));
    }

    private synchronized ConfigurableJWTProcessor<SecurityContext> jwtProcessor() throws Exception {
        if (cachedProcessor == null) {
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(URI.create(JWKS_URI).toURL());
            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(keySelector);
            cachedProcessor = processor;
        }
        return cachedProcessor;
    }
}
