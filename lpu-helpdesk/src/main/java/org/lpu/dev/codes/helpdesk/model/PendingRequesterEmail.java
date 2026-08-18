package org.lpu.dev.codes.helpdesk.model;

/**
 * Placeholder requester address used when an onsite person has no LPU email
 * on their directory record yet. Tickets stay identifiable by person type +
 * number until staff encode the real address.
 */
public final class PendingRequesterEmail {

    /** Never-resolving reserved TLD — must not be mailed. */
    public static final String DOMAIN = "pending.invalid";

    public static final String LINK_LPU_EMAIL_CATEGORY = "LINK_LPU_EMAIL";

    private PendingRequesterEmail() {
    }

    public static String forPerson(String personType, String personNo) {
        return "unlinked." + sanitize(personType) + "." + sanitize(personNo) + "@" + DOMAIN;
    }

    public static boolean isPending(String email) {
        if (email == null || email.isBlank()) {
            return true;
        }
        return email.trim().toLowerCase().endsWith("@" + DOMAIN);
    }

    private static String sanitize(String raw) {
        String value = raw == null ? "unknown" : raw.trim().toLowerCase();
        String cleaned = value.replaceAll("[^a-z0-9]+", "-");
        if (cleaned.isBlank()) {
            return "unknown";
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
