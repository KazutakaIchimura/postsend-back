package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStaffRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank String role,
        @Size(min = 8, max = 100) String password
) {}
