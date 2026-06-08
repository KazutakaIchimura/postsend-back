package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.Staff;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByEmailIgnoreCase(String email);
    List<Staff> findByIsActiveTrue(Sort sort);
}
