package org.lpu.dev.codes.helpdesk.model;

/**
 * {@code USER} is any authenticated LPU Laguna student/employee (default on
 * first sign-in). {@code ADMIN} and {@code SUPER_ADMIN} are MISD staff
 * accounts provisioned with a password by a Super Admin. {@code MONITORING}
 * is a display-only account for the live queue / ticket TV board.
 */
public enum Role {
    USER,
    ADMIN,
    SUPER_ADMIN,
    MONITORING
}
