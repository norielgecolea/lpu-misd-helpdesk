package org.lpu.dev.codes.helpdesk.dto;

import java.util.List;

public record StaffNotificationListResponse(
        List<StaffNotificationResponse> items,
        long unreadCount
) {
}
