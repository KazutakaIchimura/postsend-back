package com.example.sendmail.controller;

import com.example.sendmail.dto.response.MonitoringCycleResponse;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.service.MonitoringCycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MonitoringCycleController 統合テスト
 *
 * 設計方針:
 *   - @SpringBootTest + @AutoConfigureMockMvc で実際の SecurityFilterChain を通す
 *   - MonitoringCycleService を @MockitoBean でモックし、コントローラー層の
 *     入出力・バリデーション・認可のみ検証する
 *   - ADMIN は全利用者に対して操作可能、STAFF は担当利用者のみ（制御はサービス層）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("MonitoringCycleController 統合テスト")
class MonitoringCycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitoringCycleService monitoringCycleService;

    private MonitoringCycleResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = new MonitoringCycleResponse(
                1L, "山田太郎", "ヤマダタロウ",
                null, null,
                6,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 15),
                "特記事項なし");
    }

    // ============================================================
    // GET /api/schedule
    // ============================================================
    @Nested
    @DisplayName("GET /api/schedule — スケジュール一覧取得")
    class ListSchedule {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-MC-01: STAFF権限でスケジュール一覧が200で返る")
        void tc_mc_01_staffListsSchedule_returns200() throws Exception {
            when(monitoringCycleService.listSchedule()).thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/schedule"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].userId").value(1))
                    .andExpect(jsonPath("$[0].userName").value("山田太郎"))
                    .andExpect(jsonPath("$[0].cycleMonths").value(6));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-MC-02: ADMIN権限でもスケジュール一覧が200で返る")
        void tc_mc_02_adminListsSchedule_returns200() throws Exception {
            when(monitoringCycleService.listSchedule()).thenReturn(List.of());

            mockMvc.perform(get("/api/schedule"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("TC-MC-03: 未認証でアクセスすると401が返る")
        void tc_mc_03_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/schedule"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));

            verify(monitoringCycleService, never()).listSchedule();
        }
    }

    // ============================================================
    // GET /api/users/{userId}/monitoring-cycle
    // ============================================================
    @Nested
    @DisplayName("GET /api/users/{userId}/monitoring-cycle — モニタリング設定取得")
    class GetMonitoringCycle {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-MC-04: 存在するuserIdを指定するとモニタリング設定が200で返る")
        void tc_mc_04_existingUserId_returns200() throws Exception {
            when(monitoringCycleService.getByUserId(1L)).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/users/{userId}/monitoring-cycle", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.cycleMonths").value(6))
                    .andExpect(jsonPath("$.nextMonitoringDate").value("2026-09-01"));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-MC-05: モニタリング設定が未登録のuserIdを指定すると404が返る")
        void tc_mc_05_monitoringCycleNotFound_returns404() throws Exception {
            when(monitoringCycleService.getByUserId(999L))
                    .thenThrow(new ResourceNotFoundException("モニタリング設定が見つかりません: userId=999"));

            mockMvc.perform(get("/api/users/{userId}/monitoring-cycle", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("モニタリング設定が見つかりません: userId=999"));
        }

        @Test
        @DisplayName("TC-MC-06: 未認証でアクセスすると401が返る")
        void tc_mc_06_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/users/{userId}/monitoring-cycle", 1L))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // PUT /api/users/{userId}/monitoring-cycle
    // ============================================================
    @Nested
    @DisplayName("PUT /api/users/{userId}/monitoring-cycle — モニタリング設定保存")
    class SaveMonitoringCycle {

        @Test
        @WithMockUser(username = "admin@example.com", roles = "ADMIN")
        @DisplayName("TC-MC-07: ADMIN権限・有効なリクエストで保存に成功し200が返る")
        void tc_mc_07_adminValidRequest_returns200() throws Exception {
            when(monitoringCycleService.save(eq(1L), any(), eq("admin@example.com"), eq(true)))
                    .thenReturn(sampleResponse);

            mockMvc.perform(put("/api/users/{userId}/monitoring-cycle", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "cycleMonths": 6,
                                      "nextMonitoringDate": "2026-09-01",
                                      "nextPlanDraftDate": "2026-08-15",
                                      "nextPlanDate": "2026-09-15",
                                      "notes": "特記事項なし"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cycleMonths").value(6))
                    .andExpect(jsonPath("$.userId").value(1));

            verify(monitoringCycleService).save(eq(1L), any(), eq("admin@example.com"), eq(true));
        }

        @Test
        @WithMockUser(username = "staff@example.com", roles = "STAFF")
        @DisplayName("TC-MC-08: STAFF権限の場合 isAdmin=false でサービスが呼ばれる")
        void tc_mc_08_staffRole_isAdminFalse() throws Exception {
            when(monitoringCycleService.save(eq(1L), any(), eq("staff@example.com"), eq(false)))
                    .thenReturn(sampleResponse);

            mockMvc.perform(put("/api/users/{userId}/monitoring-cycle", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cycleMonths": 3}
                                    """))
                    .andExpect(status().isOk());

            verify(monitoringCycleService).save(eq(1L), any(), eq("staff@example.com"), eq(false));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-MC-09: cycleMonthsがnullの場合400バリデーションエラーが返る（@NotNull）")
        void tc_mc_09_cycleMonthsNull_returns400() throws Exception {
            mockMvc.perform(put("/api/users/{userId}/monitoring-cycle", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"notes": "メモのみ"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.cycleMonths").exists());

            verify(monitoringCycleService, never()).save(any(), any(), any(), anyBoolean());
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-MC-10: cycleMonthsが0の場合400バリデーションエラーが返る（@Min(1)）")
        void tc_mc_10_cycleMonthsZero_returns400() throws Exception {
            mockMvc.perform(put("/api/users/{userId}/monitoring-cycle", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cycleMonths": 0}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.cycleMonths").exists());

            verify(monitoringCycleService, never()).save(any(), any(), any(), anyBoolean());
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-MC-11: cycleMonthsが13の場合400バリデーションエラーが返る（@Max(12)）")
        void tc_mc_11_cycleMonthsExceedsMax_returns400() throws Exception {
            mockMvc.perform(put("/api/users/{userId}/monitoring-cycle", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cycleMonths": 13}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.cycleMonths").exists());

            verify(monitoringCycleService, never()).save(any(), any(), any(), anyBoolean());
        }

        @Test
        @DisplayName("TC-MC-12: 未認証でアクセスすると401が返る")
        void tc_mc_12_unauthenticated_returns401() throws Exception {
            mockMvc.perform(put("/api/users/{userId}/monitoring-cycle", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"cycleMonths": 6}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }
}
