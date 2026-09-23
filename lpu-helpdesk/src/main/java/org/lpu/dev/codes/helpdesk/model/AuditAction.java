package org.lpu.dev.codes.helpdesk.model;

public enum AuditAction {
    STAFF_LOGIN(AuditResourceType.AUTH, "Signed in"),
    STAFF_LOGIN_FAILED(AuditResourceType.AUTH, "Sign-in failed"),
    PASSWORD_CHANGED(AuditResourceType.AUTH, "Changed password"),
    PASSWORD_RESET_REQUESTED(AuditResourceType.AUTH, "Requested password reset"),
    PASSWORD_RESET_COMPLETED(AuditResourceType.AUTH, "Reset password"),
    PROFILE_UPDATED(AuditResourceType.AUTH, "Updated own profile"),

    ACCOUNT_CREATED(AuditResourceType.ACCOUNT, "Created staff account"),
    ACCOUNT_UPDATED(AuditResourceType.ACCOUNT, "Updated staff account"),
    ACCOUNT_ACTIVATED(AuditResourceType.ACCOUNT, "Activated staff account"),
    ACCOUNT_DEACTIVATED(AuditResourceType.ACCOUNT, "Deactivated staff account"),

    TICKET_ASSIGNED(AuditResourceType.TICKET, "Assigned ticket"),
    TICKET_STATUS_CHANGED(AuditResourceType.TICKET, "Changed ticket status"),
    TICKET_AUTO_CLOSED(AuditResourceType.TICKET, "Auto-closed ticket"),
    TICKET_REPLIED(AuditResourceType.TICKET, "Replied on ticket"),

    QUEUE_WALK_IN(AuditResourceType.QUEUE, "Logged walk-in ticket"),
    QUEUE_CLAIMED(AuditResourceType.QUEUE, "Claimed queue ticket"),
    QUEUE_COMPLETED(AuditResourceType.QUEUE, "Completed queue ticket"),
    QUEUE_REQUEUED(AuditResourceType.QUEUE, "Returned ticket to queue"),
    QUEUE_HELD(AuditResourceType.QUEUE, "Held queue ticket"),
    QUEUE_TRANSFER_REQUESTED(AuditResourceType.QUEUE, "Requested queue transfer"),
    QUEUE_TRANSFER_APPROVED(AuditResourceType.QUEUE, "Approved queue transfer"),
    QUEUE_TRANSFER_REJECTED(AuditResourceType.QUEUE, "Rejected queue transfer"),
    QUEUE_TRANSFER_CANCELLED(AuditResourceType.QUEUE, "Cancelled queue transfer"),

    CATEGORY_CREATED(AuditResourceType.CATEGORY, "Created kiosk choice"),
    CATEGORY_UPDATED(AuditResourceType.CATEGORY, "Updated kiosk choice"),
    CATEGORY_DELETED(AuditResourceType.CATEGORY, "Deleted kiosk choice"),

    DIRECTORY_EMAIL_ENCODED(AuditResourceType.DIRECTORY, "Encoded directory email"),
    DIRECTORY_PERSON_LINKED(AuditResourceType.DIRECTORY, "Linked directory person");

    private final AuditResourceType resourceType;
    private final String label;

    AuditAction(AuditResourceType resourceType, String label) {
        this.resourceType = resourceType;
        this.label = label;
    }

    public AuditResourceType resourceType() {
        return resourceType;
    }

    public String label() {
        return label;
    }
}
