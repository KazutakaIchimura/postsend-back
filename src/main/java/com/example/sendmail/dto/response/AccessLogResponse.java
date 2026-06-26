package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.AccessLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AccessLogResponse {
    private Long id;
    private String staffEmail;
    private String action;
    private String resourceType;
    private Long resourceId;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;

    public static AccessLogResponse from(AccessLog log) {
        return AccessLogResponse.builder()
                .id(log.getId())
                .staffEmail(log.getStaffEmail())
                .action(log.getAction())
                .resourceType(log.getResourceType())
                .resourceId(log.getResourceId())
                .details(log.getDetails())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
