package com.example.sendmail.dto.response;

import java.time.LocalDateTime;

public record MailSendBatchResponse(Long batchId, LocalDateTime sentAt, int updatedCount, String notes) {}
