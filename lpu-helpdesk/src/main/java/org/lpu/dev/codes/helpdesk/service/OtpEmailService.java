package org.lpu.dev.codes.helpdesk.service;

import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OtpEmailService {

    private static final Logger log = LogManager.getLogger(OtpEmailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public OtpEmailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Async
    public void sendOtpEmail(String toEmail, String code, int expirationMinutes) {
        String subject = "Your LPU MISD Helpdesk sign-in code";
        String body = "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f3f4f6;padding:24px;margin:0;'>"
                + "<div style='max-width:480px;margin:0 auto;background:#fff;border-radius:12px;padding:32px;'>"
                + "<p style='margin:0 0 8px;font-size:11px;font-weight:700;letter-spacing:2px;color:#7a2342;text-transform:uppercase;'>"
                + "LPU Laguna MISD Helpdesk</p>"
                + "<h1 style='margin:0 0 16px;font-size:22px;color:#111827;'>Your sign-in code</h1>"
                + "<p style='color:#374151;font-size:15px;line-height:1.5;'>Use the code below to finish signing in. "
                + "This code expires in <strong>" + expirationMinutes + " minute" + (expirationMinutes == 1 ? "" : "s") + "</strong>.</p>"
                + "<p style='margin:28px 0;text-align:center;'>"
                + "<span style='display:inline-block;background:#f3e8ec;color:#7a2342;font-size:32px;font-weight:700;"
                + "letter-spacing:8px;padding:16px 24px;border-radius:8px;'>" + escape(code) + "</span></p>"
                + "<p style='color:#6b7280;font-size:13px;line-height:1.5;'>If you did not request this code, you can safely ignore this email.</p>"
                + "</div></body></html>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.getFromAddress(), mailProperties.getFromName());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("OTP email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}", toEmail, e);
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
