package com.example.sendmail.controller;

import com.example.sendmail.dto.request.ChangePasswordRequest;
import com.example.sendmail.dto.request.SaveAccessibilitySettingsRequest;
import com.example.sendmail.dto.response.StaffResponse;
import com.example.sendmail.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/login  → Spring Security が処理
    // POST /api/auth/logout → Spring Security が処理

    @GetMapping("/me")
    public StaffResponse me(Authentication auth) {
        return StaffResponse.from(authService.getCurrentStaff(auth.getName()));
    }

    @PostMapping("/password/change")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req, Authentication auth) {
        authService.changePassword(auth.getName(), req.newPassword());
    }

    @PutMapping("/accessibility-settings")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveAccessibilitySettings(
            @RequestBody SaveAccessibilitySettingsRequest req, Authentication auth) {
        authService.saveAccessibilitySettings(auth.getName(), req);
    }
}
