package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddOfficeToUserRequest(
        @NotNull @Positive Long officeId
) {}
