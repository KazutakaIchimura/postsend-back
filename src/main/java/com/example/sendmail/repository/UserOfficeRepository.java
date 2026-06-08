package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.UserOffice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserOfficeRepository extends JpaRepository<UserOffice, Long> {

    @Query("SELECT uo FROM UserOffice uo JOIN FETCH uo.office WHERE uo.user.id = :userId")
    List<UserOffice> findByUserIdWithOffice(@Param("userId") Long userId);

    Optional<UserOffice> findByUserIdAndOfficeId(Long userId, Long officeId);
}
