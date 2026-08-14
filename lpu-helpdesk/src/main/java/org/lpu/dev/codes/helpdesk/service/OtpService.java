package org.lpu.dev.codes.helpdesk.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.lpu.dev.codes.helpdesk.config.OtpProperties;
import org.lpu.dev.codes.helpdesk.model.OtpCode;
import org.lpu.dev.codes.helpdesk.repository.OtpCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {

    private final OtpCodeRepository otpCodeRepository;
    private final OtpEmailService otpEmailService;
    private final PasswordEncoder passwordEncoder;
    private final OtpProperties otpProperties;
    private final SecureRandom random = new SecureRandom();

    public OtpService(
            OtpCodeRepository otpCodeRepository,
            OtpEmailService otpEmailService,
            PasswordEncoder passwordEncoder,
            OtpProperties otpProperties
    ) {
        this.otpCodeRepository = otpCodeRepository;
        this.otpEmailService = otpEmailService;
        this.passwordEncoder = passwordEncoder;
        this.otpProperties = otpProperties;
    }

    /** Generates, stores, and emails a new code; returns its validity window in ms. */
    @Transactional
    public long requestOtp(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        otpCodeRepository.invalidateActiveByEmail(normalizedEmail);

        String code = generateNumericCode(otpProperties.getLength());
        OtpCode otpCode = new OtpCode();
        otpCode.setEmail(normalizedEmail);
        otpCode.setCodeHash(passwordEncoder.encode(code));
        otpCode.setExpiresAt(Instant.now().plus(otpProperties.getExpirationMinutes(), ChronoUnit.MINUTES));
        otpCodeRepository.persist(otpCode);

        otpEmailService.sendOtpEmail(normalizedEmail, code, otpProperties.getExpirationMinutes());

        return otpProperties.getExpirationMinutes() * 60_000L;
    }

    @Transactional
    public boolean verifyOtp(String email, String code) {
        String normalizedEmail = email.trim().toLowerCase();
        Optional<OtpCode> maybeOtp = otpCodeRepository.findLatestActiveByEmail(normalizedEmail, Instant.now());
        if (maybeOtp.isEmpty()) {
            return false;
        }

        OtpCode otp = maybeOtp.get();
        if (otp.getAttempts() >= otpProperties.getMaxAttempts()) {
            otp.setConsumed(true);
            otpCodeRepository.save(otp);
            return false;
        }

        boolean matches = passwordEncoder.matches(code.trim(), otp.getCodeHash());
        otp.setAttempts(otp.getAttempts() + 1);
        if (matches) {
            otp.setConsumed(true);
        }
        otpCodeRepository.save(otp);
        return matches;
    }

    private String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
