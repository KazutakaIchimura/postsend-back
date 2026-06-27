package com.example.sendmail.controller;

import com.example.sendmail.dto.request.SaveMonitoringCycleRequest;
import com.example.sendmail.dto.response.MonitoringCycleResponse;
import com.example.sendmail.service.MonitoringCycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MonitoringCycleController {

    private final MonitoringCycleService monitoringCycleService;

    /** スケジュール一覧（全有効利用者、STAFF以上） */
    @GetMapping("/api/schedule")
    public List<MonitoringCycleResponse> listSchedule() {
        return monitoringCycleService.listSchedule();
    }

    /** 特定利用者のモニタリング設定取得（STAFF以上） */
    @GetMapping("/api/users/{userId}/monitoring-cycle")
    public MonitoringCycleResponse get(@PathVariable Long userId) {
        return monitoringCycleService.getByUserId(userId);
    }

    /**
     * モニタリング設定の保存（新規作成 or 更新）。
     * ADMIN は全利用者に対して操作可能、STAFF は担当利用者のみ。
     */
    @PutMapping("/api/users/{userId}/monitoring-cycle")
    public MonitoringCycleResponse save(
            @PathVariable Long userId,
            @Valid @RequestBody SaveMonitoringCycleRequest req,
            Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return monitoringCycleService.save(userId, req, authentication.getName(), isAdmin);
    }
}
