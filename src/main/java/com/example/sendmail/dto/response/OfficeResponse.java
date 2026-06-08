package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.Office;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class OfficeResponse {
    private Long id;
    private String name;
    private String postalCode;
    private String building;
    private String address;
    private String phone;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OfficeResponse from(Office office) {
        return OfficeResponse.builder()
                .id(office.getId())
                .name(office.getName())
                .postalCode(office.getPostalCode())
                .building(office.getBuilding())
                .address(office.getAddress())
                .phone(office.getPhone())
                .isActive(office.getIsActive())
                .createdAt(office.getCreatedAt())
                .updatedAt(office.getUpdatedAt())
                .build();
    }
}
