package com.example.sendmail.dto.response;

import com.example.sendmail.config.YearMonthSerializer;
import com.example.sendmail.domain.entity.MailSend;
import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.domain.enums.SendType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MailSendResponse(
        Long id,
        Long userId,
        String userName,
        Long officeId,
        String officeName,
        SendType sendType,
        @Schema(type = "string", pattern = "yyyy-MM", example = "2026-06",
                description = "送付対象月（\"yyyy-MM\" 形式で出力される。日付情報は持たない）")
        @JsonSerialize(using = YearMonthSerializer.class)
        LocalDate sendMonth,
        SendStatus status,
        @JsonProperty("isOverdue") Boolean isOverdue,
        Long batchId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static MailSendResponse from(MailSend ms, LocalDate thisMonth) {
        boolean overdue = ms.getStatus() == SendStatus.PENDING
                && ms.getSendMonth().isBefore(thisMonth);
        return new MailSendResponse(
                ms.getId(),
                ms.getUser().getId(),
                ms.getUser().getName(),
                ms.getOffice().getId(),
                ms.getOffice().getName(),
                ms.getSendType(),
                ms.getSendMonth(),
                ms.getStatus(),
                overdue,
                ms.getBatch() != null ? ms.getBatch().getId() : null,
                ms.getCreatedAt(),
                ms.getUpdatedAt()
        );
    }
}
