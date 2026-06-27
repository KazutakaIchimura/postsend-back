package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    Page<User> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.assignedStaff WHERE u.isActive = true ORDER BY u.name")
    List<User> findAllActiveWithAssignedStaff();
}
