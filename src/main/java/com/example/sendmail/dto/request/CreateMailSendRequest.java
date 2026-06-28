package com.example.sendmail.dto.request;

import com.example.sendmail.domain.enums.SendType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateMailSendRequest(
        @NotNull Long userId,
        @NotNull Long officeId,
        @NotNull SendType sendType,
        @NotNull LocalDate sendMonth
) {}
