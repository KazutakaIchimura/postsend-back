package com.example.sendmail.dto.response;

import com.example.sendmail.domain.enums.SendType;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardResponse(
        long pendingCount,
        long overdueCount,
        long sentThisMonthCount,
        String currentMonth,
        List<OverdueMonthCount> overdueMonths,
        List<RecentHistoryItem> recentHistory
) {
    public record OverdueMonthCount(String month, long count) {}

    public record RecentHistoryItem(
            Long id,
            String officeName,
            String userName,
            SendType sendType,
            LocalDateTime sentAt
    ) {}
}
