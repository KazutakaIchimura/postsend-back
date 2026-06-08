package com.example.sendmail.service;

import com.example.sendmail.domain.entity.Role;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.dto.request.CreateStaffRequest;
import com.example.sendmail.dto.request.UpdateStaffRequest;
import com.example.sendmail.dto.response.StaffResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.RoleRepository;
import com.example.sendmail.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepository staffRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private static final Sort NAME_ASC = Sort.by("name").ascending();

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaffs(boolean includeInactive) {
        List<Staff> staffs = includeInactive
                ? staffRepository.findAll(NAME_ASC)
                : staffRepository.findByIsActiveTrue(NAME_ASC);
        return staffs.stream().map(StaffResponse::from).toList();
    }

    @Transactional
    public StaffResponse createStaff(CreateStaffRequest req) {
        String email = req.getEmail().toLowerCase();
        if (staffRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new DuplicateResourceException("このメールアドレスは既に使用されています: " + email);
        }
        Role role = findRoleByName(req.getRole());
        Staff staff = new Staff();
        staff.setName(req.getName());
        staff.setEmail(email);
        staff.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        staff.setRole(role);
        return StaffResponse.from(staffRepository.save(staff));
    }

    @Transactional
    public StaffResponse updateStaff(Long id, UpdateStaffRequest req) {
        Staff staff = findStaffById(id);
        Role role = findRoleByName(req.getRole());
        staff.setName(req.getName());
        staff.setRole(role);
        if (req.getPassword() != null) {
            if (req.getPassword().isBlank()) {
                throw new IllegalArgumentException("パスワードに空白のみは指定できません");
            }
            staff.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            staff.setForcePasswordChange(false);
        }
        return StaffResponse.from(staffRepository.save(staff));
    }

    @Transactional
    public void activateStaff(Long id) {
        Staff staff = findStaffById(id);
        if (!Boolean.TRUE.equals(staff.getIsActive())) {
            staff.setIsActive(true);
            staffRepository.save(staff);
        }
    }

    @Transactional
    public void deactivateStaff(Long id) {
        Staff staff = findStaffById(id);
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (staff.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new IllegalArgumentException("自分自身を無効化することはできません");
        }
        if (Boolean.TRUE.equals(staff.getIsActive())) {
            staff.setIsActive(false);
            staffRepository.save(staff);
        }
    }

    private Staff findStaffById(Long id) {
        return staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("スタッフが見つかりません: " + id));
    }

    private Staff findActiveStaffById(Long id) {
        Staff staff = findStaffById(id);
        if (!Boolean.TRUE.equals(staff.getIsActive())) {
            throw new ResourceNotFoundException("スタッフが見つかりません: " + id);
        }
        return staff;
    }

    private Role findRoleByName(String name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("ロールが見つかりません: " + name));
    }
}
