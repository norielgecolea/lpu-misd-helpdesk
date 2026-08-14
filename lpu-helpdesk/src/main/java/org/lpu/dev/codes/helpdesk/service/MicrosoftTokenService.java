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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.MsalProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validates Microsoft Entra ID (Azure AD) ID tokens produced by MSAL on the
 * frontend: verifies the RS256 signature against Microsoft's published JWKS,
 * then checks issuer, audience, and expiry before trusting any claims.
 */
@Service
public class MicrosoftTokenService {

    private static final Logger log = LogManager.getLogger(MicrosoftTokenService.class);

    private final MsalProperties msalProperties;

    private volatile ConfigurableJWTProcessor<SecurityContext> cachedProcessor;
    private volatile String cachedTenantId;

    public MicrosoftTokenService(MsalProperties msalProperties) {
        this.msalProperties = msalProperties;
    }

    public record MicrosoftIdentity(String email, String name) {
    }

    public MicrosoftIdentity validate(String idToken) {
        if (!msalProperties.isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Microsoft sign-in is not configured on the server yet"
            );
        }

        try {
            JWTClaimsSet claims = jwtProcessor().process(idToken, null);

            String expectedIssuer = "https://login.microsoftonline.com/" + msalProperties.getTenantId() + "/v2.0";
            if (!expectedIssuer.equals(claims.getIssuer())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token issuer");
            }

            List<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(msalProperties.getClientId())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token audience");
            }

            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token has expired");
            }

            String email = firstNonBlank(
                    claims.getStringClaim("email"),
                    claims.getStringClaim("preferred_username")
            );
            if (email == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token did not include an email claim");
            }
            String name = claims.getStringClaim("name");

            return new MicrosoftIdentity(email.toLowerCase(), name);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Microsoft ID token validation failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to validate Microsoft sign-in token");
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private synchronized ConfigurableJWTProcessor<SecurityContext> jwtProcessor() throws Exception {
        String tenantId = msalProperties.getTenantId();
        if (cachedProcessor == null || !tenantId.equals(cachedTenantId)) {
            URI jwksUri = URI.create("https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys");
            JWKSource<SecurityContext> keySource = new RemoteJWKSet<>(jwksUri.toURL());
            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);

            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            processor.setJWSKeySelector(keySelector);

            cachedProcessor = processor;
            cachedTenantId = tenantId;
        }
        return cachedProcessor;
    }
}
