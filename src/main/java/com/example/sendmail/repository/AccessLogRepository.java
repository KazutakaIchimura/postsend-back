package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.AccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {
    Page<AccessLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
