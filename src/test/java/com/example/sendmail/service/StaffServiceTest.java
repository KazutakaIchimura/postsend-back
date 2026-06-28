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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.sendmail.dto.request.UpdateStaffRequest;
import com.example.sendmail.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // SecurityContextHolder に認証情報をセットするヘルパー
    private void setCurrentUser(String email) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(
                email, null, List.of()));
        SecurityContextHolder.setContext(ctx);
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
        verify(accessLogService).log(eq("CREATE"), eq("STAFF"), eq(10L));
    }

    // ============================================================
    // createStaff
    // ============================================================

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

    // ============================================================
    // updateStaff
    // ============================================================

    @Test
    @DisplayName("updateStaff: 空白のみのパスワードを指定するとIllegalArgumentExceptionをスロー")
    void updateStaff_blankPassword_throwsIllegalArgumentException() {
        Staff staff = new Staff();
        staff.setId(1L);
        staff.setName("田中太郎");
        staff.setPasswordHash("existingHash");
        staff.setRole(staffRole);
        staff.setCreatedAt(LocalDateTime.now());
        staff.setUpdatedAt(LocalDateTime.now());

        UpdateStaffRequest req = new UpdateStaffRequest();
        req.setName("田中太郎");
        req.setRole("STAFF");
        req.setPassword("   "); // 空白のみ

        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));

        assertThatThrownBy(() -> staffService.updateStaff(1L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("パスワードに空白のみは指定できません");

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStaff: パスワード未指定の場合は既存ハッシュが維持される")
    void updateStaff_noPassword_keepsExistingHash() {
        Staff staff = new Staff();
        staff.setId(1L);
        staff.setName("田中太郎");
        staff.setPasswordHash("existingHash");
        staff.setRole(staffRole);
        staff.setCreatedAt(LocalDateTime.now());
        staff.setUpdatedAt(LocalDateTime.now());

        UpdateStaffRequest req = new UpdateStaffRequest();
        req.setName("田中次郎");
        req.setRole("STAFF");
        // password は設定しない

        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(staffRepository.save(staff)).thenReturn(staff);

        staffService.updateStaff(1L, req);

        assertThat(staff.getPasswordHash()).isEqualTo("existingHash");
        assertThat(staff.getName()).isEqualTo("田中次郎");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("updateStaff: 存在しないIDを指定するとResourceNotFoundExceptionをスロー")
    void updateStaff_nonExistingId_throwsResourceNotFoundException() {
        when(staffRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.updateStaff(999L, new UpdateStaffRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("スタッフが見つかりません: 999");
    }

    // ============================================================
    // activateStaff
    // ============================================================

    @Test
    @DisplayName("activateStaff: 無効スタッフを有効化するとisActiveがtrueになりsaveが呼ばれる")
    void activateStaff_inactiveStaff_becomesActive() {
        Staff staff = new Staff();
        staff.setId(2L);
        staff.setIsActive(false);

        when(staffRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(staffRepository.save(staff)).thenReturn(staff);

        staffService.activateStaff(2L);

        assertThat(staff.getIsActive()).isTrue();
        verify(staffRepository).save(staff);
        verify(accessLogService).log("ACTIVATE", "STAFF", 2L);
    }

    @Test
    @DisplayName("activateStaff: 既に有効なスタッフはsaveが呼ばれない（冪等）")
    void activateStaff_alreadyActive_saveNotCalled() {
        Staff staff = new Staff();
        staff.setId(1L);
        staff.setIsActive(true);

        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));

        staffService.activateStaff(1L);

        verify(staffRepository, never()).save(any());
        verify(accessLogService).log("ACTIVATE", "STAFF", 1L);
    }

    // ============================================================
    // deactivateStaff
    // ============================================================

    @Test
    @DisplayName("deactivateStaff: 自分自身を無効化しようとするとIllegalArgumentExceptionをスロー")
    void deactivateStaff_selfDeactivation_throwsIllegalArgumentException() {
        setCurrentUser("current@example.com");

        Staff staff = new Staff();
        staff.setId(1L);
        staff.setEmail("current@example.com");
        staff.setIsActive(true);
        staff.setRole(staffRole);

        when(staffRepository.findById(1L)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffService.deactivateStaff(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("自分自身を無効化することはできません");

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateStaff: 最後のADMINを無効化しようとするとIllegalArgumentExceptionをスロー")
    void deactivateStaff_lastAdmin_throwsIllegalArgumentException() {
        setCurrentUser("other@example.com");

        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setName("ADMIN");

        Staff staff = new Staff();
        staff.setId(2L);
        staff.setEmail("admin@example.com");
        staff.setIsActive(true);
        staff.setRole(adminRole);

        when(staffRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(staffRepository.countByRole_NameAndIsActiveTrue("ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> staffService.deactivateStaff(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最後のADMINは無効化できません");

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivateStaff: 条件を満たす場合はisActiveがfalseになりsaveが呼ばれる")
    void deactivateStaff_validConditions_setsIsActiveFalse() {
        setCurrentUser("other@example.com");

        Staff staff = new Staff();
        staff.setId(3L);
        staff.setEmail("target@example.com");
        staff.setIsActive(true);
        staff.setRole(staffRole); // STAFF ロールなのでADMINチェックは不要

        when(staffRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(staffRepository.save(staff)).thenReturn(staff);

        staffService.deactivateStaff(3L);

        assertThat(staff.getIsActive()).isFalse();
        verify(staffRepository).save(staff);
        verify(accessLogService).log("DEACTIVATE", "STAFF", 3L);
    }
}
