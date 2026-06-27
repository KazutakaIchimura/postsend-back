package com.example.sendmail.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_cycles")
@Getter
@Setter
@NoArgsConstructor
public class MonitoringCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** モニタリング周期（月数）: 1 / 3 / 6 / 12 */
    @Column(name = "cycle_months", nullable = false)
    private Integer cycleMonths = 6;

    /** 次回モニタリング予定日 */
    @Column(name = "next_monitoring_date")
    private LocalDate nextMonitoringDate;

    /** 次回サービス等利用計画案の提出予定日 */
    @Column(name = "next_plan_draft_date")
    private LocalDate nextPlanDraftDate;

    /** 次回サービス等利用計画（本計画）の提出予定日 */
    @Column(name = "next_plan_date")
    private LocalDate nextPlanDate;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
