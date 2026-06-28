package com.example.sendmail.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SaveMonitoringCycleRequest(
        @NotNull(message = "モニタリング周期を選択してください")
        @Min(value = 1, message = "周期は1ヶ月以上で指定してください")
        @Max(value = 12, message = "周期は12ヶ月以内で指定してください")
        Integer cycleMonths,
        LocalDate nextMonitoringDate,
        LocalDate nextPlanDraftDate,
        LocalDate nextPlanDate,
        String notes
) {}
