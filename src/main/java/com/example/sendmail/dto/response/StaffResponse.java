package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.Staff;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record StaffResponse(
        Long id,
        String name,
        String email,
        Long roleId,
        String role,
        @JsonProperty("isActive") Boolean isActive,
        Boolean forcePasswordChange,
        String accessibilitySettings,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static StaffResponse from(Staff staff) {
        return new StaffResponse(
                staff.getId(),
                staff.getName(),
                staff.getEmail(),
                staff.getRole().getId(),
                staff.getRole().getName(),
                staff.getIsActive(),
                staff.getForcePasswordChange(),
                staff.getAccessibilitySettings(),
                staff.getCreatedAt(),
                staff.getUpdatedAt()
        );
    }
}
