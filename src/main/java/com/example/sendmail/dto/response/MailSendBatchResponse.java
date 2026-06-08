package com.example.sendmail.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MailSendBatchResponse {
    private Long batchId;
    private LocalDateTime sentAt;
    private int updatedCount;
    private String notes;
}
