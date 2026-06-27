package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.Staff;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StaffResponse {
    private Long id;
    private String name;
    private String email;
    private Long roleId;
    private String role;
    private Boolean isActive;
    private Boolean forcePasswordChange;
    private String accessibilitySettings;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static StaffResponse from(Staff staff) {
        return StaffResponse.builder()
                .id(staff.getId())
                .name(staff.getName())
                .email(staff.getEmail())
                .roleId(staff.getRole().getId())
                .role(staff.getRole().getName())
                .isActive(staff.getIsActive())
                .forcePasswordChange(staff.getForcePasswordChange())
                .accessibilitySettings(staff.getAccessibilitySettings())
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .build();
    }
}
