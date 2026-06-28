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
                .map(row -> new DashboardResponse.OverdueMonthCount(
                        row.getSendMonth().format(MONTH_FORMATTER),
                        row.getCount()
                ))
                .toList();

        List<DashboardResponse.RecentHistoryItem> recentHistory = mailSendRepository
                .findRecentSentHistory(SendStatus.SENT, PageRequest.of(0, 5))
                .stream()
                .map(row -> new DashboardResponse.RecentHistoryItem(
                        row.getId(),
                        row.getOfficeName(),
                        row.getUserName(),
                        row.getSendType(),
                        row.getSentAt()
                ))
                .toList();

        return new DashboardResponse(
                pendingCount,
                overdueCount,
                sentThisMonthCount,
                thisMonth.format(MONTH_FORMATTER),
                overdueMonths,
                recentHistory
        );
    }
}
