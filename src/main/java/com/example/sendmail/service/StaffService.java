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
import org.springframework.dao.DataIntegrityViolationException;
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
    private final AccessLogService accessLogService;

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
        String email = req.email().toLowerCase();
        Role role = findRoleByName(req.role());
        Staff staff = new Staff();
        staff.setName(req.name());
        staff.setEmail(email);
        staff.setPasswordHash(passwordEncoder.encode(req.password()));
        staff.setRole(role);
        StaffResponse result;
        try {
            result = StaffResponse.from(staffRepository.save(staff));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateResourceException("このメールアドレスは既に使用されています: " + email);
        }
        accessLogService.log("CREATE", "STAFF", result.id());
        return result;
    }

    @Transactional
    public StaffResponse updateStaff(Long id, UpdateStaffRequest req) {
        Staff staff = findStaffById(id);
        Role role = findRoleByName(req.role());
        staff.setName(req.name());
        staff.setRole(role);
        if (req.password() != null) {
            if (req.password().isBlank()) {
                throw new IllegalArgumentException("パスワードに空白のみは指定できません");
            }
            staff.setPasswordHash(passwordEncoder.encode(req.password()));
            staff.setForcePasswordChange(false);
        }
        StaffResponse result = StaffResponse.from(staffRepository.save(staff));
        accessLogService.log("UPDATE", "STAFF", id);
        return result;
    }

    @Transactional
    public void activateStaff(Long id) {
        Staff staff = findStaffById(id);
        if (!Boolean.TRUE.equals(staff.getIsActive())) {
            staff.setIsActive(true);
            staffRepository.save(staff);
        }
        accessLogService.log("ACTIVATE", "STAFF", id);
    }

    @Transactional
    public void deactivateStaff(Long id) {
        Staff staff = findStaffById(id);
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (staff.getEmail().equalsIgnoreCase(currentEmail)) {
            throw new IllegalArgumentException("自分自身を無効化することはできません");
        }
        if ("ADMIN".equals(staff.getRole().getName())
                && staffRepository.countByRole_NameAndIsActiveTrue("ADMIN") <= 1) {
            throw new IllegalArgumentException("最後のADMINは無効化できません");
        }
        if (Boolean.TRUE.equals(staff.getIsActive())) {
            staff.setIsActive(false);
            staffRepository.save(staff);
        }
        accessLogService.log("DEACTIVATE", "STAFF", id);
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
