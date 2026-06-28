package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOfficeRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String officeType,
        @Size(max = 8) String postalCode,
        @Size(max = 200) String building,
        @Size(max = 500) String address,
        @Size(max = 20) String phone
) {}
