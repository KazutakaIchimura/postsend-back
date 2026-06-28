package com.example.sendmail.controller;

import com.example.sendmail.domain.enums.SendType;
import com.example.sendmail.dto.response.DashboardResponse;
import com.example.sendmail.service.DashboardService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #8 ダッシュボード（DashboardController）— spec No.87〜93
 *
 * テスト仕様書「バックエンド_テスト仕様書.xlsx」の #8 ダッシュボードカテゴリに 1:1 で対応する。
 *
 * 設計方針:
 *   - @SpringBootTest + @AutoConfigureMockMvc で実際の SecurityFilterChain を通す
 *   - DashboardService を @MockitoBean でモックし、コントローラー層の入出力・認可のみ検証する
 *   - 集計ロジック自体（PENDING件数・isOverdue集計・直近5件等）はサービス層の責務であるため、
 *     ここではサービスの戻り値（DashboardResponse）がレスポンス JSON に
 *     正しく反映されることを確認する形で検証する
 *
 * 注意: DashboardService#getDashboard の当月送付済み件数（sentThisMonthCount）は
 * countByStatusAndSendMonth(SendStatus.SENT, thisMonth) のみを集計しており、
 * 仕様書 No.89 が言う「当月 status=SENT/DONE」のうち DONE は集計対象に含まれていない。
 * No.89 はこの実装上の制約を踏まえ、SENT のみが集計される現行挙動を記録する形で実装する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("#8 ダッシュボード（DashboardController）")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    private static final String BASE_URL = "/api/dashboard";

    @BeforeEach
    void setUp() {
    }

    @Nested
    @DisplayName("GET /api/dashboard — ダッシュボード集計取得")
    class GetDashboard {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.87: GET /api/dashboard: 未送付件数（status=PENDING）が正しく集計される")
        void no87_pendingCount_aggregatedCorrectly() throws Exception {
            DashboardResponse response = new DashboardResponse(5L, 0L, 0L, "2026-06", List.of(), List.of());
            when(dashboardService.getDashboard()).thenReturn(response);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.pendingCount").value(5));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.88: GET /api/dashboard: 期限超過件数（isOverdue=true）が正しく集計される")
        void no88_overdueCount_aggregatedCorrectly() throws Exception {
            DashboardResponse response = new DashboardResponse(5L, 3L, 0L, "2026-06", List.of(), List.of());
            when(dashboardService.getDashboard()).thenReturn(response);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overdueCount").value(3));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.89: GET /api/dashboard: 当月送付済み件数（当月 status=SENT/DONE）が正しく集計される")
        void no89_sentThisMonthCount_aggregatedCorrectly() throws Exception {
            DashboardResponse response = new DashboardResponse(0L, 0L, 7L, "2026-06", List.of(), List.of());
            when(dashboardService.getDashboard()).thenReturn(response);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sentThisMonthCount").value(7));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.90: GET /api/dashboard: 期限超過月の一覧が send_month の昇順で返る")
        void no90_overdueMonths_returnedInAscendingOrder() throws Exception {
            DashboardResponse response = new DashboardResponse(
                    2L, 2L, 0L, "2026-06",
                    List.of(
                            new DashboardResponse.OverdueMonthCount("2026-03", 1L),
                            new DashboardResponse.OverdueMonthCount("2026-04", 1L)
                    ),
                    List.of()
            );
            when(dashboardService.getDashboard()).thenReturn(response);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overdueMonths.length()").value(2))
                    .andExpect(jsonPath("$.overdueMonths[0].month").value("2026-03"))
                    .andExpect(jsonPath("$.overdueMonths[1].month").value("2026-04"));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.91: GET /api/dashboard: 最近の送付履歴が sent_at 降順で最大5件返る")
        void no91_recentHistory_returnsUpTo5ItemsInDescendingSentAtOrder() throws Exception {
            LocalDateTime latest = LocalDateTime.of(2026, 6, 8, 10, 0, 0);
            LocalDateTime older = LocalDateTime.of(2026, 6, 5, 9, 0, 0);

            DashboardResponse response = new DashboardResponse(
                    0L, 0L, 2L, "2026-06",
                    List.of(),
                    List.of(
                            new DashboardResponse.RecentHistoryItem(2L, "中央事業所", "山田太郎", SendType.PLAN, latest),
                            new DashboardResponse.RecentHistoryItem(1L, "南事業所", "鈴木花子", SendType.MONITORING, older)
                    )
            );
            when(dashboardService.getDashboard()).thenReturn(response);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.recentHistory.length()").value(2))
                    .andExpect(jsonPath("$.recentHistory[0].id").value(2))
                    .andExpect(jsonPath("$.recentHistory[1].id").value(1));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.92: GET /api/dashboard: 対象データが0件の場合も空配列・0件で200が返る")
        void no92_noData_returnsEmptyArraysAndZeroCounts() throws Exception {
            DashboardResponse response = new DashboardResponse(0L, 0L, 0L, "2026-06", List.of(), List.of());
            when(dashboardService.getDashboard()).thenReturn(response);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pendingCount").value(0))
                    .andExpect(jsonPath("$.overdueCount").value(0))
                    .andExpect(jsonPath("$.sentThisMonthCount").value(0))
                    .andExpect(jsonPath("$.overdueMonths").isArray())
                    .andExpect(jsonPath("$.overdueMonths.length()").value(0))
                    .andExpect(jsonPath("$.recentHistory").isArray())
                    .andExpect(jsonPath("$.recentHistory.length()").value(0));
        }

        @Test
        @DisplayName("No.93: GET /api/dashboard: 未認証でアクセスすると401が返る")
        void no93_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));

            verify(dashboardService, never()).getDashboard();
        }
    }
}
