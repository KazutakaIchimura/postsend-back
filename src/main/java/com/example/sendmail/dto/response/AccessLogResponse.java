package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.AccessLog;

import java.time.LocalDateTime;

public record AccessLogResponse(
        Long id,
        String staffEmail,
        String action,
        String resourceType,
        Long resourceId,
        String details,
        String ipAddress,
        LocalDateTime createdAt
) {
    public static AccessLogResponse from(AccessLog log) {
        return new AccessLogResponse(
                log.getId(), log.getStaffEmail(), log.getAction(), log.getResourceType(),
                log.getResourceId(), log.getDetails(), log.getIpAddress(), log.getCreatedAt()
        );
    }
}
