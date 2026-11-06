package org.lpu.dev.codes.helpdesk.dto;

/** Gate directory photo filename plus the keys used to match a ticket requester. */
public record DirectoryPhotoRow(String number, String rfid, String email, String photo) {
}
