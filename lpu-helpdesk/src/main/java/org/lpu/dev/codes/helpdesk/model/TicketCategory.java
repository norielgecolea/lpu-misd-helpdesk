package org.lpu.dev.codes.helpdesk.model;

/**
 * Default starter categories — placeholder until MISD provides the official
 * concern-type list. Safe to rename/extend later without a schema change
 * since this is stored as a VARCHAR, not a Postgres enum type.
 */
public enum TicketCategory {
    NETWORK_INTERNET("Network / Internet"),
    HARDWARE_EQUIPMENT("Hardware / Equipment"),
    ACCOUNT_PASSWORD("Account & Password"),
    SOFTWARE_SYSTEM_ACCESS("Software / System Access"),
    EMAIL_OUTLOOK("Email / Outlook"),
    LINK_LPU_EMAIL("Link LPU Email"),
    OTHERS("Others");

    private final String label;

    TicketCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
