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

    /**
     * 一覧表示用に、利用者・事業所をJOIN FETCHしたうえで条件を絞り込んで取得する。
     * findAll()で全件取得してからJavaのStreamでフィルタする実装だと、件数増加時に
     * 全件がメモリへロードされ、かつ各レコードのuser/office（LAZY）アクセスでN+1が
     * 発生するため、クエリ側で絞り込み・JOIN FETCHする。
     */
    @Query("""
            SELECT ms FROM MailSend ms
            JOIN FETCH ms.user u
            JOIN FETCH ms.office o
            WHERE (:status IS NULL OR ms.status = :status)
              AND (:sendMonth IS NULL OR ms.sendMonth = :sendMonth)
              AND (:userId IS NULL OR u.id = :userId)
              AND (:officeId IS NULL OR o.id = :officeId)
              AND (:dateFrom IS NULL OR ms.sendMonth >= :dateFrom)
              AND (:dateTo IS NULL OR ms.sendMonth <= :dateTo)
            ORDER BY ms.sendMonth, o.name, u.name
            """)
    List<MailSend> findFiltered(
            @Param("status") SendStatus status,
            @Param("sendMonth") LocalDate sendMonth,
            @Param("userId") Long userId,
            @Param("officeId") Long officeId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

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
