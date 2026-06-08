package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.MailSend;
import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.domain.enums.SendType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MailSendRepository extends JpaRepository<MailSend, Long> {
    boolean existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
            Long userId, Long officeId, SendType sendType, LocalDate sendMonth);

    long countByStatus(SendStatus status);
    long countByStatusAndSendMonth(SendStatus status, LocalDate sendMonth);
    long countByBatchId(Long batchId);

    @Query("""
            SELECT ms.sendMonth as sendMonth, COUNT(ms) as count
            FROM MailSend ms
            WHERE ms.status = :status AND ms.sendMonth < :month
            GROUP BY ms.sendMonth
            ORDER BY ms.sendMonth
            """)
    List<OverdueMonthProjection> countGroupedByMonthBefore(
            @Param("status") SendStatus status,
            @Param("month") LocalDate month);

    @Query("""
            SELECT ms.id as id, o.name as officeName, u.name as userName,
                   ms.sendType as sendType, b.sentAt as sentAt
            FROM MailSend ms
            JOIN ms.office o
            JOIN ms.user u
            LEFT JOIN ms.batch b
            WHERE ms.status = :status
            ORDER BY CASE WHEN b.sentAt IS NULL THEN 1 ELSE 0 END, b.sentAt DESC
            """)
    List<RecentSentHistoryProjection> findRecentSentHistory(
            @Param("status") SendStatus status,
            Pageable pageable);

    interface OverdueMonthProjection {
        LocalDate getSendMonth();
        Long getCount();
    }

    interface RecentSentHistoryProjection {
        Long getId();
        String getOfficeName();
        String getUserName();
        SendType getSendType();
        LocalDateTime getSentAt();
    }
}
