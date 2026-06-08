package com.example.sendmail.service;

import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.dto.response.DashboardResponse;
import com.example.sendmail.repository.MailSendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MailSendRepository mailSendRepository;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

        long pendingCount = mailSendRepository.countByStatus(SendStatus.PENDING);
        long sentThisMonthCount = mailSendRepository.countByStatusAndSendMonth(SendStatus.SENT, thisMonth);

        var overdueMonthsRaw = mailSendRepository.countGroupedByMonthBefore(SendStatus.PENDING, thisMonth);
        long overdueCount = overdueMonthsRaw.stream()
                .mapToLong(MailSendRepository.OverdueMonthProjection::getCount)
                .sum();

        List<DashboardResponse.OverdueMonthCount> overdueMonths = overdueMonthsRaw.stream()
                .map(row -> DashboardResponse.OverdueMonthCount.builder()
                        .month(row.getSendMonth().format(MONTH_FORMATTER))
                        .count(row.getCount())
                        .build())
                .toList();

        List<DashboardResponse.RecentHistoryItem> recentHistory = mailSendRepository
                .findRecentSentHistory(SendStatus.SENT, PageRequest.of(0, 5))
                .stream()
                .map(row -> DashboardResponse.RecentHistoryItem.builder()
                        .id(row.getId())
                        .officeName(row.getOfficeName())
                        .userName(row.getUserName())
                        .sendType(row.getSendType())
                        .sentAt(row.getSentAt())
                        .build())
                .toList();

        return DashboardResponse.builder()
                .pendingCount(pendingCount)
                .overdueCount(overdueCount)
                .sentThisMonthCount(sentThisMonthCount)
                .currentMonth(thisMonth.format(MONTH_FORMATTER))
                .overdueMonths(overdueMonths)
                .recentHistory(recentHistory)
                .build();
    }
}
