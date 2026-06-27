package com.example.sendmail.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SaveMonitoringCycleRequest {

    @NotNull(message = "モニタリング周期を選択してください")
    @Min(value = 1, message = "周期は1ヶ月以上で指定してください")
    @Max(value = 12, message = "周期は12ヶ月以内で指定してください")
    private Integer cycleMonths;

    private LocalDate nextMonitoringDate;
    private LocalDate nextPlanDraftDate;
    private LocalDate nextPlanDate;

    private String notes;
}
