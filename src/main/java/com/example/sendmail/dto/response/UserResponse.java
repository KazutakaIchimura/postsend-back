package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String name,
        String nameKana,
        LocalDate birthDate,
        String notes,
        String recipientNumber,
        String disabilitySupportCategory,
        @JsonProperty("isActive") Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long assignedStaffId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String assignedStaffName,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<OfficeResponse> offices
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getNameKana(),
                user.getBirthDate(),
                user.getNotes(),
                user.getRecipientNumber(),
                user.getDisabilitySupportCategory(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getAssignedStaff() != null ? user.getAssignedStaff().getId() : null,
                user.getAssignedStaff() != null ? user.getAssignedStaff().getName() : null,
                null
        );
    }

    public static UserResponse fromWithOffices(User user, List<OfficeResponse> offices) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getNameKana(),
                user.getBirthDate(),
                user.getNotes(),
                user.getRecipientNumber(),
                user.getDisabilitySupportCategory(),
                user.getIsActive(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getAssignedStaff() != null ? user.getAssignedStaff().getId() : null,
                user.getAssignedStaff() != null ? user.getAssignedStaff().getName() : null,
                offices
        );
    }
}
