package com.example.sendmail.service;

import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.dto.request.SaveAccessibilitySettingsRequest;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthService authService;

    private Staff activeStaff;

    @BeforeEach
    void setUp() {
        activeStaff = new Staff();
        activeStaff.setId(1L);
        activeStaff.setEmail("user@example.com");
        activeStaff.setPasswordHash("oldHash");
        activeStaff.setForcePasswordChange(true);
        activeStaff.setIsActive(true);
    }

    // ============================================================
    // changePassword
    // ============================================================

    @Test
    @DisplayName("changePassword: 成功するとpasswordHashが更新されforcePasswordChangeがfalseになる")
    void changePassword_success_updatesHashAndClearsForceFlag() {
        when(staffRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(activeStaff));
        when(passwordEncoder.encode("newSecurePass")).thenReturn("newHash");
        when(staffRepository.save(activeStaff)).thenReturn(activeStaff);

        authService.changePassword("user@example.com", "newSecurePass");

        assertThat(activeStaff.getPasswordHash()).isEqualTo("newHash");
        assertThat(activeStaff.getForcePasswordChange()).isFalse();
        verify(staffRepository).save(activeStaff);
    }

    @Test
    @DisplayName("changePassword: 存在しないメールアドレスを指定するとResourceNotFoundExceptionをスロー")
    void changePassword_unknownEmail_throwsResourceNotFoundException() {
        when(staffRepository.findByEmailIgnoreCase("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword("unknown@example.com", "newPass123"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("スタッフが見つかりません");

        verify(staffRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword: passwordEncoderが呼ばれ平文パスワードがハッシュ化される")
    void changePassword_passwordEncoderIsCalled() {
        when(staffRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(activeStaff));
        when(passwordEncoder.encode("rawPassword")).thenReturn("hashedPassword");
        when(staffRepository.save(any())).thenReturn(activeStaff);

        authService.changePassword("user@example.com", "rawPassword");

        verify(passwordEncoder).encode("rawPassword");
        assertThat(activeStaff.getPasswordHash()).isEqualTo("hashedPassword");
    }

    // ============================================================
    // saveAccessibilitySettings
    // ============================================================

    @Test
    @DisplayName("saveAccessibilitySettings: 設定がJSON文字列にシリアライズされてstaffに保存される")
    void saveAccessibilitySettings_success_serializedAndSaved() throws Exception {
        when(staffRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(activeStaff));
        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"fontSize\":\"large\",\"furigana\":true,\"bgColor\":\"sepia\"}");
        when(staffRepository.save(activeStaff)).thenReturn(activeStaff);

        SaveAccessibilitySettingsRequest req = new SaveAccessibilitySettingsRequest();
        req.setFontSize("large");
        req.setFurigana(true);
        req.setBgColor("sepia");

        authService.saveAccessibilitySettings("user@example.com", req);

        assertThat(activeStaff.getAccessibilitySettings())
                .isEqualTo("{\"fontSize\":\"large\",\"furigana\":true,\"bgColor\":\"sepia\"}");
        verify(staffRepository).save(activeStaff);
    }

    @Test
    @DisplayName("saveAccessibilitySettings: JSON変換失敗でも例外を飲み込んでsaveが呼ばれる")
    void saveAccessibilitySettings_jsonFailure_savesAnyway() throws Exception {
        when(staffRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(activeStaff));
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("serialization error"));
        when(staffRepository.save(activeStaff)).thenReturn(activeStaff);

        SaveAccessibilitySettingsRequest req = new SaveAccessibilitySettingsRequest();

        authService.saveAccessibilitySettings("user@example.com", req);

        // 例外を飲み込んでもsaveは呼ばれる
        verify(staffRepository).save(activeStaff);
    }

    @Test
    @DisplayName("saveAccessibilitySettings: 存在しないメールアドレスを指定するとResourceNotFoundExceptionをスロー")
    void saveAccessibilitySettings_unknownEmail_throwsResourceNotFoundException() {
        when(staffRepository.findByEmailIgnoreCase("unknown@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.saveAccessibilitySettings(
                "unknown@example.com", new SaveAccessibilitySettingsRequest()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(staffRepository, never()).save(any());
    }
}
