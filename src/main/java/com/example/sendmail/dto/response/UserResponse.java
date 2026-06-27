package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String name;
    private String nameKana;
    private LocalDate birthDate;
    private String notes;
    private String recipientNumber;
    private String disabilitySupportCategory;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long assignedStaffId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String assignedStaffName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<OfficeResponse> offices;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .nameKana(user.getNameKana())
                .birthDate(user.getBirthDate())
                .notes(user.getNotes())
                .recipientNumber(user.getRecipientNumber())
                .disabilitySupportCategory(user.getDisabilitySupportCategory())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .assignedStaffId(user.getAssignedStaff() != null ? user.getAssignedStaff().getId() : null)
                .assignedStaffName(user.getAssignedStaff() != null ? user.getAssignedStaff().getName() : null)
                .build();
    }

    public static UserResponse fromWithOffices(User user, List<OfficeResponse> offices) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .nameKana(user.getNameKana())
                .birthDate(user.getBirthDate())
                .notes(user.getNotes())
                .recipientNumber(user.getRecipientNumber())
                .disabilitySupportCategory(user.getDisabilitySupportCategory())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .assignedStaffId(user.getAssignedStaff() != null ? user.getAssignedStaff().getId() : null)
                .assignedStaffName(user.getAssignedStaff() != null ? user.getAssignedStaff().getName() : null)
                .offices(offices)
                .build();
    }
}
