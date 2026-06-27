package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.MonitoringCycle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MonitoringCycleRepository extends JpaRepository<MonitoringCycle, Long> {

    Optional<MonitoringCycle> findByUserId(Long userId);

    @Query("""
        SELECT mc FROM MonitoringCycle mc
        JOIN FETCH mc.user u
        LEFT JOIN FETCH u.assignedStaff s
        WHERE u.isActive = true
        ORDER BY mc.nextMonitoringDate ASC NULLS LAST
        """)
    List<MonitoringCycle> findAllWithUserOrderByNextMonitoring();
}
