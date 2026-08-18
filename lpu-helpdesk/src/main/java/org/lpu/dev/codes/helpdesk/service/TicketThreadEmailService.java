package org.lpu.dev.codes.helpdesk.service;

import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lpu.dev.codes.helpdesk.config.MailProperties;
import org.lpu.dev.codes.helpdesk.model.PendingRequesterEmail;
import org.lpu.dev.codes.helpdesk.model.Ticket;
import org.lpu.dev.codes.helpdesk.model.TicketStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * All ticket emails share one subject per ticket so clients keep a single thread.
 * Subject format: {@code Help Desk Ticket #<ticketNumber>}
 */
@Service
public class TicketThreadEmailService {

    private static final Logger log = LogManager.getLogger(TicketThreadEmailService.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;

    public TicketThreadEmailService(JavaMailSender mailSender, MailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    public static String threadSubject(String ticketNumber) {
        return "Help Desk Ticket #" + (ticketNumber != null ? ticketNumber.trim() : "");
    }

    /** @deprecated ticket subject is no longer included in the email subject line. */
    @Deprecated
    public static String threadSubject(String ticketNumber, String ticketSubject) {
        return threadSubject(ticketNumber);
    }

    public String mailDomain() {
        String from = mailProperties.getFromAddress();
        int at = from != null ? from.indexOf('@') : -1;
        if (at > 0 && at < from.length() - 1) {
            return from.substring(at + 1);
        }
        return "helpdesk.lpulaguna.edu.ph";
    }

    public String newMessageId(long ticketId, String kind) {
        return "<ticket-" + ticketId + "-" + kind + "-" + UUID.randomUUID() + "@" + mailDomain() + ">";
    }

    @Async
    public void sendTicketCreatedAsync(Ticket ticket, String rootMessageId) {
        if (ticket == null) {
            return;
        }
        String body = "<p style='margin:0 0 12px;font-size:15px;'>Your ticket <strong>"
                + escape(ticket.getTicketNumber())
                + "</strong> has been created.</p>"
                + "<p style='margin:0 0 8px;font-size:14px;color:#52525b;'><strong>Subject:</strong> "
                + escape(ticket.getSubject()) + "</p>"
                + "<div style='white-space:pre-wrap;font-size:14px;color:#3f3f46;margin:0 0 16px;'>"
                + escape(ticket.getDescription()) + "</div>"
                + ctaButton("View Ticket", viewTicketUrl());
        sendThreaded(
                ticket.getTicketNumber(),
                ticket.getRequesterEmail(),
                null,
                rootMessageId,
                body
        );
    }

    @Async
    public void sendAgentReplyAsync(
            String ticketNumber,
            String studentEmail,
            String rootMessageId,
            String messageId,
            String messageBody
    ) {
        String body = "<p style='margin:0 0 12px;font-size:15px;'>You have a new reply from <strong>MIS Support</strong>.</p>"
                + "<p style='margin:0 0 6px;font-size:12px;font-weight:700;letter-spacing:0.06em;color:#71717a;text-transform:uppercase;'>Message</p>"
                + "<div style='white-space:pre-wrap;font-size:15px;margin:0 0 16px;'>" + escape(messageBody) + "</div>"
                + ctaButton("View Ticket", viewTicketUrl());
        sendThreaded(ticketNumber, studentEmail, rootMessageId, messageId, body);
    }

    @Async
    public void sendStatusChangeAsync(
            Ticket ticket,
            TicketStatus newStatus,
            String rootMessageId,
            String messageId
    ) {
        if (ticket == null || newStatus == null) {
            return;
        }
        String statusLabel = statusLabel(newStatus);
        StringBuilder body = new StringBuilder();
        body.append("<p style='margin:0 0 12px;font-size:15px;'>Your ticket <strong>")
                .append(escape(ticket.getTicketNumber()))
                .append("</strong> is now <strong>")
                .append(escape(statusLabel))
                .append("</strong>.</p>");

        if (newStatus == TicketStatus.CLOSED) {
            body.append("<p style='margin:0 0 16px;font-size:15px;'>Please sign in to the Helpdesk portal to rate your experience (CSM).</p>");
            body.append(ctaButton("Sign in to rate", viewTicketUrl()));
        } else {
            body.append(ctaButton("View Ticket", viewTicketUrl()));
        }

        sendThreaded(
                ticket.getTicketNumber(),
                ticket.getRequesterEmail(),
                rootMessageId,
                messageId,
                body.toString()
        );
    }

    private void sendThreaded(
            String ticketNumber,
            String studentEmail,
            String rootMessageId,
            String messageId,
            String innerHtml
    ) {
        try {
        if (studentEmail == null || studentEmail.isBlank() || PendingRequesterEmail.isPending(studentEmail)) {
            log.warn("Skipping ticket email for {} — no deliverable student address", ticketNumber);
            return;
        }
            if (messageId == null || messageId.isBlank()) {
                log.warn("Skipping ticket email for {} — missing Message-ID", ticketNumber);
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailProperties.getFromAddress(), mailProperties.getFromName());
            helper.setTo(studentEmail.trim().toLowerCase());
            helper.setSubject(threadSubject(ticketNumber));
            helper.setText(wrapHtml(ticketNumber, innerHtml), true);

            message.setHeader("Message-ID", messageId);
            if (rootMessageId != null && !rootMessageId.isBlank()) {
                message.setHeader("In-Reply-To", rootMessageId);
                message.setHeader("References", rootMessageId);
            }

            mailSender.send(message);
            log.info("Ticket email sent ticket={} messageId={}", ticketNumber, messageId);
        } catch (Exception e) {
            log.error("Failed to send ticket email for {}", ticketNumber, e);
        }
    }

    private String viewTicketUrl() {
        return mailProperties.trimmedPublicBaseUrl() + "/";
    }

    private static String statusLabel(TicketStatus status) {
        return switch (status) {
            case OPEN -> "Open";
            case IN_PROGRESS -> "In Progress";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
        };
    }

    private static String ctaButton(String label, String url) {
        return "<p style='margin:20px 0 0;text-align:left;'>"
                + "<a href='" + escape(url) + "' style='display:inline-block;background:#8d2546;color:#fff;"
                + "font-size:14px;font-weight:700;text-decoration:none;padding:10px 18px;border-radius:8px;'>"
                + escape(label) + "</a></p>";
    }

    private static String wrapHtml(String ticketNumber, String innerHtml) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;color:#18181b;line-height:1.5;background:#f4f4f5;padding:24px;margin:0;'>"
                + "<div style='max-width:560px;margin:0 auto;background:#fff;border-radius:12px;padding:28px;border:1px solid #e4e4e7;'>"
                + "<p style='margin:0 0 4px;font-size:11px;font-weight:700;letter-spacing:0.08em;color:#8d2546;text-transform:uppercase;'>"
                + "LPU Laguna MISD Helpdesk</p>"
                + "<p style='margin:0 0 20px;font-size:13px;color:#71717a;'>" + escape(threadSubject(ticketNumber)) + "</p>"
                + innerHtml
                + "<hr style='border:none;border-top:1px solid #e4e4e7;margin:24px 0 12px;'/>"
                + "<p style='margin:0;font-size:12px;color:#a1a1aa;'>This message is part of your helpdesk ticket conversation.</p>"
                + "</div></body></html>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
