package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.MonitoringCycle;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MonitoringCycleResponse {

    private Long userId;
    private String userName;
    private String userNameKana;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long assignedStaffId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String assignedStaffName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer cycleMonths;
    private LocalDate nextMonitoringDate;
    private LocalDate nextPlanDraftDate;
    private LocalDate nextPlanDate;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String notes;

    public static MonitoringCycleResponse from(MonitoringCycle mc) {
        var user = mc.getUser();
        var assignedStaff = user.getAssignedStaff();
        return MonitoringCycleResponse.builder()
                .userId(user.getId())
                .userName(user.getName())
                .userNameKana(user.getNameKana())
                .assignedStaffId(assignedStaff != null ? assignedStaff.getId() : null)
                .assignedStaffName(assignedStaff != null ? assignedStaff.getName() : null)
                .cycleMonths(mc.getCycleMonths())
                .nextMonitoringDate(mc.getNextMonitoringDate())
                .nextPlanDraftDate(mc.getNextPlanDraftDate())
                .nextPlanDate(mc.getNextPlanDate())
                .notes(mc.getNotes())
                .build();
    }

    /** モニタリング設定が未登録の利用者用（全日付 null のプレースホルダー） */
    public static MonitoringCycleResponse fromUser(com.example.sendmail.domain.entity.User user) {
        var assignedStaff = user.getAssignedStaff();
        return MonitoringCycleResponse.builder()
                .userId(user.getId())
                .userName(user.getName())
                .userNameKana(user.getNameKana())
                .assignedStaffId(assignedStaff != null ? assignedStaff.getId() : null)
                .assignedStaffName(assignedStaff != null ? assignedStaff.getName() : null)
                .cycleMonths(null)
                .nextMonitoringDate(null)
                .nextPlanDraftDate(null)
                .nextPlanDate(null)
                .notes(null)
                .build();
    }
}
