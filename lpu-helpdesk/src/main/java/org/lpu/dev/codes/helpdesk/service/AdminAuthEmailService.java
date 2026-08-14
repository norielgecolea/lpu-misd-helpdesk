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
public class AdminAuthEmailService {

    private static final Logger log = LogManager.getLogger(AdminAuthEmailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public AdminAuthEmailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String name, String resetUrl, int expiresMinutes) {
        String subject = "Reset your MISD Helpdesk password";
        String greeting = name != null && !name.isBlank() ? escape(name) : "there";
        String body = "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;background:#f3f4f6;padding:24px;margin:0;'>"
                + "<div style='max-width:480px;margin:0 auto;background:#fff;border-radius:12px;padding:32px;'>"
                + "<p style='margin:0 0 8px;font-size:11px;font-weight:700;letter-spacing:2px;color:#7a2342;text-transform:uppercase;'>"
                + "LPU Laguna MISD Helpdesk</p>"
                + "<h1 style='margin:0 0 16px;font-size:22px;color:#111827;'>Password reset</h1>"
                + "<p style='color:#374151;font-size:15px;line-height:1.5;'>Hi " + greeting + ",</p>"
                + "<p style='color:#374151;font-size:15px;line-height:1.5;'>We received a request to reset your admin portal password. "
                + "This link expires in <strong>" + expiresMinutes + " minutes</strong>.</p>"
                + "<p style='margin:28px 0;text-align:center;'>"
                + "<a href='" + escape(resetUrl) + "' style='display:inline-block;background:#8d2546;color:#fff;font-size:14px;"
                + "font-weight:700;text-decoration:none;padding:12px 22px;border-radius:8px;'>Reset password</a></p>"
                + "<p style='color:#6b7280;font-size:13px;line-height:1.5;'>If the button does not work, copy this link into your browser:<br/>"
                + "<span style='word-break:break-all;color:#374151;'>" + escape(resetUrl) + "</span></p>"
                + "<p style='color:#6b7280;font-size:13px;line-height:1.5;'>If you did not request this, you can ignore this email.</p>"
                + "</div></body></html>";
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.getFromAddress(), mailProperties.getFromName());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}", toEmail, e);
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
