package com.example.sendmail.dto.response;

import com.example.sendmail.domain.entity.Role;

public record RoleResponse(Long id, String name) {
    public static RoleResponse from(Role role) {
        return new RoleResponse(role.getId(), role.getName());
    }
}
