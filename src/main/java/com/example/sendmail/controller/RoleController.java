package com.example.sendmail.controller;

import com.example.sendmail.dto.response.RoleResponse;
import com.example.sendmail.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleRepository roleRepository;

    @GetMapping
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream()
                .map(RoleResponse::from)
                .toList();
    }
}
