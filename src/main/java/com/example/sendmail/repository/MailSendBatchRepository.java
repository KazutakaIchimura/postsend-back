package com.example.sendmail.repository;

import com.example.sendmail.domain.entity.MailSendBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailSendBatchRepository extends JpaRepository<MailSendBatch, Long> {
}
