package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.Office;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record OfficeResponse(
        Long id,
        String name,
        String officeType,
        String postalCode,
        String building,
        String address,
        String phone,
        @JsonProperty("isActive") Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OfficeResponse from(Office office) {
        return new OfficeResponse(
                office.getId(),
                office.getName(),
                office.getOfficeType(),
                office.getPostalCode(),
                office.getBuilding(),
                office.getAddress(),
                office.getPhone(),
                office.getIsActive(),
                office.getCreatedAt(),
                office.getUpdatedAt()
        );
    }
}
