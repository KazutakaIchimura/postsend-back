package com.example.sendmail.service;

import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.dto.request.SaveAccessibilitySettingsRequest;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.StaffRepository;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public Staff getCurrentStaff(String email) {
        return staffRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("スタッフが見つかりません: " + email));
    }

    @Transactional
    public void changePassword(String email, String newPassword) {
        Staff staff = getCurrentStaff(email);
        staff.setPasswordHash(passwordEncoder.encode(newPassword));
        staff.setForcePasswordChange(false);
        staffRepository.save(staff);
    }

    @Transactional
    public void saveAccessibilitySettings(String email, SaveAccessibilitySettingsRequest req) {
        Staff staff = getCurrentStaff(email);
        try {
            staff.setAccessibilitySettings(objectMapper.writeValueAsString(req));
        } catch (Exception e) {
            // JSON変換失敗は無視してそのまま続行
        }
        staffRepository.save(staff);
    }
}
