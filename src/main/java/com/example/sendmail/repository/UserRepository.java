package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Page<User> findByIsActiveTrue(Pageable pageable);
}
