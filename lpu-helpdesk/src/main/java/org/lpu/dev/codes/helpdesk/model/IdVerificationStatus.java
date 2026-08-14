package org.lpu.dev.codes.helpdesk.model;

/**
 * Student ID verification lifecycle. Staff accounts stay on {@code NONE}.
 */
public enum IdVerificationStatus {
    NONE,
    PENDING,
    VERIFIED,
    REJECTED
}
