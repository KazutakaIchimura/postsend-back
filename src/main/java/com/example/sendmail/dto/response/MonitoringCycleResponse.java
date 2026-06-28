package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.MonitoringCycle;
import com.example.sendmail.domain.entity.User;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

public record MonitoringCycleResponse(
        Long userId,
        String userName,
        String userNameKana,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long assignedStaffId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String assignedStaffName,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer cycleMonths,
        LocalDate nextMonitoringDate,
        LocalDate nextPlanDraftDate,
        LocalDate nextPlanDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String notes
) {
    public static MonitoringCycleResponse from(MonitoringCycle mc) {
        var user = mc.getUser();
        var assignedStaff = user.getAssignedStaff();
        return new MonitoringCycleResponse(
                user.getId(),
                user.getName(),
                user.getNameKana(),
                assignedStaff != null ? assignedStaff.getId() : null,
                assignedStaff != null ? assignedStaff.getName() : null,
                mc.getCycleMonths(),
                mc.getNextMonitoringDate(),
                mc.getNextPlanDraftDate(),
                mc.getNextPlanDate(),
                mc.getNotes()
        );
    }

    /** モニタリング設定が未登録の利用者用（全日付 null のプレースホルダー） */
    public static MonitoringCycleResponse fromUser(User user) {
        var assignedStaff = user.getAssignedStaff();
        return new MonitoringCycleResponse(
                user.getId(),
                user.getName(),
                user.getNameKana(),
                assignedStaff != null ? assignedStaff.getId() : null,
                assignedStaff != null ? assignedStaff.getName() : null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
