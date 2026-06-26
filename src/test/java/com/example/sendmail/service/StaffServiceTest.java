package com.example.sendmail.service;

import com.example.sendmail.domain.entity.Role;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.dto.request.CreateStaffRequest;
import com.example.sendmail.dto.response.StaffResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.repository.RoleRepository;
import com.example.sendmail.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaffService")
class StaffServiceTest {

    @Mock
    private StaffRepository staffRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccessLogService accessLogService;

    @InjectMocks
    private StaffService staffService;

    private Role staffRole;

    @BeforeEach
    void setUp() {
        staffRole = new Role();
        staffRole.setId(2L);
        staffRole.setName("STAFF");
    }

    @Test
    @DisplayName("createStaff: 正常登録でStaffResponseを返す")
    void createStaff_success() {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setName("田中太郎");
        req.setEmail("Tanaka@Example.com");
        req.setPassword("password123");
        req.setRole("STAFF");

        Staff saved = new Staff();
        saved.setId(10L);
        saved.setName("田中太郎");
        saved.setEmail("tanaka@example.com");
        saved.setPasswordHash("hashed");
        saved.setRole(staffRole);
        saved.setIsActive(true);
        saved.setForcePasswordChange(true);
        saved.setCreatedAt(LocalDateTime.now());
        saved.setUpdatedAt(LocalDateTime.now());

        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(staffRepository.save(any(Staff.class))).thenReturn(saved);

        StaffResponse result = staffService.createStaff(req);

        assertThat(result.getId()).isEqualTo(10L);
        // メールアドレスは小文字正規化されて保存される
        assertThat(result.getEmail()).isEqualTo("tanaka@example.com");
    }

    @Test
    @DisplayName("createStaff: DB一意制約違反でメール固有の DuplicateResourceException をスロー")
    void createStaff_duplicateEmail_throwsDuplicateResourceException() {
        CreateStaffRequest req = new CreateStaffRequest();
        req.setName("重複スタッフ");
        req.setEmail("Duplicate@Example.com");
        req.setPassword("password123");
        req.setRole("STAFF");

        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(staffRepository.save(any(Staff.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index or primary key violation"));

        assertThatThrownBy(() -> staffService.createStaff(req))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("このメールアドレスは既に使用されています")
                .hasMessageContaining("duplicate@example.com");
    }
}
