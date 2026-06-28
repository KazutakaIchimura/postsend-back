package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateUserRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String nameKana,
        @Past LocalDate birthDate,
        @Size(max = 2000) String notes,
        @Size(max = 20) String recipientNumber,
        @Size(max = 20) String disabilitySupportCategory,
        Long assignedStaffId
) {}
