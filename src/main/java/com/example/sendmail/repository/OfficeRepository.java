package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.Office;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficeRepository extends JpaRepository<Office, Long> {
    List<Office> findByIsActiveTrue();
}
