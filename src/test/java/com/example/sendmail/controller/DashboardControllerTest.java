package com.example.sendmail.controller;

import com.example.sendmail.domain.entity.Role;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.domain.enums.SendType;
import com.example.sendmail.repository.MailSendRepository;
import com.example.sendmail.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DashboardController の統合テスト。
 *
 * 設計方針:
 *   - @SpringBootTest で実際の SecurityFilterChain を通す
 *   - MailSendRepository / StaffRepository を @MockitoBean でモック
 *   - @WithMockUser で認証状態を表現し、DB 依存なしにインメモリで完結
 *   - TC-DASH-07 のみ実際のログイン/ログアウトフローを使用
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("DashboardController 統合テスト")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailSendRepository mailSendRepository;

    @MockitoBean
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String DASHBOARD_URL = "/api/dashboard";
    private static final String LOGIN_EMAIL   = "staff@example.com";
    private static final String RAW_PASSWORD  = "password123";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @BeforeEach
    void setUp() {
        // --- StaffRepository スタブ（TC-DASH-07 のログインフロー用）---
        Role role = new Role();
        role.setId(1L);
        role.setName("STAFF");

        Staff staff = new Staff();
        staff.setId(1L);
        staff.setName("テストスタッフ");
        staff.setEmail(LOGIN_EMAIL);
        staff.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        staff.setRole(role);
        staff.setIsActive(true);
        staff.setForcePasswordChange(false);

        when(staffRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(staffRepository.findByEmailIgnoreCase(LOGIN_EMAIL)).thenReturn(Optional.of(staff));

        // --- MailSendRepository デフォルトスタブ（各テストでオーバーライド可能）---
        when(mailSendRepository.countByStatus(any())).thenReturn(0L);
        when(mailSendRepository.countByStatusAndSendMonth(any(), any())).thenReturn(0L);
        when(mailSendRepository.countGroupedByMonthBefore(any(), any()))
                .thenReturn(Collections.emptyList());
        when(mailSendRepository.findRecentSentHistory(any(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
    }

    // ============================================================
    // GET /api/dashboard — 正常系
    // ============================================================
    @Nested
    @DisplayName("GET /api/dashboard — 正常系")
    class Success {

        /**
         * TC-DASH-01: 全データが存在する場合
         * pendingCount=3, overdueCount=2（2025-12 × 1件 + 2026-01 × 1件）
         * sentThisMonthCount=1, recentHistory=1件
         * sentAt フィールドが存在すること、sendType=PLAN であることを検証する
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-01: 全データあり - 各フィールドが正しく返される")
        void should_returnFullDashboardData_when_allDataExists() throws Exception {
            LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

            // 延滞月別データのモック
            MailSendRepository.OverdueMonthProjection dec2025 =
                    mock(MailSendRepository.OverdueMonthProjection.class);
            when(dec2025.getSendMonth()).thenReturn(LocalDate.of(2025, 12, 1));
            when(dec2025.getCount()).thenReturn(1L);

            MailSendRepository.OverdueMonthProjection jan2026 =
                    mock(MailSendRepository.OverdueMonthProjection.class);
            when(jan2026.getSendMonth()).thenReturn(LocalDate.of(2026, 1, 1));
            when(jan2026.getCount()).thenReturn(1L);

            // 最近の送付履歴のモック
            MailSendRepository.RecentSentHistoryProjection history =
                    mock(MailSendRepository.RecentSentHistoryProjection.class);
            when(history.getId()).thenReturn(10L);
            when(history.getOfficeName()).thenReturn("東京事務所");
            when(history.getUserName()).thenReturn("山田太郎");
            when(history.getSendType()).thenReturn(SendType.PLAN);
            when(history.getSentAt()).thenReturn(LocalDateTime.of(2026, 6, 1, 10, 0));

            when(mailSendRepository.countByStatus(SendStatus.PENDING)).thenReturn(3L);
            when(mailSendRepository.countByStatusAndSendMonth(SendStatus.SENT, thisMonth)).thenReturn(1L);
            when(mailSendRepository.countGroupedByMonthBefore(eq(SendStatus.PENDING), eq(thisMonth)))
                    .thenReturn(List.of(dec2025, jan2026));
            when(mailSendRepository.findRecentSentHistory(eq(SendStatus.SENT), any(Pageable.class)))
                    .thenReturn(List.of(history));

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.pendingCount").value(3))
                    .andExpect(jsonPath("$.overdueCount").value(2))
                    .andExpect(jsonPath("$.sentThisMonthCount").value(1))
                    // currentMonth が "yyyy-MM" 形式で今月を返すこと
                    .andExpect(jsonPath("$.currentMonth").value(thisMonth.format(MONTH_FORMATTER)))
                    // overdueMonths に2件含まれ、月・件数が昇順に正しく返ること
                    .andExpect(jsonPath("$.overdueMonths").isArray())
                    .andExpect(jsonPath("$.overdueMonths.length()").value(2))
                    .andExpect(jsonPath("$.overdueMonths[0].month").value("2025-12"))
                    .andExpect(jsonPath("$.overdueMonths[0].count").value(1))
                    .andExpect(jsonPath("$.overdueMonths[1].month").value("2026-01"))
                    .andExpect(jsonPath("$.overdueMonths[1].count").value(1))
                    // recentHistory に1件含まれ、各フィールドが正しいこと
                    .andExpect(jsonPath("$.recentHistory").isArray())
                    .andExpect(jsonPath("$.recentHistory.length()").value(1))
                    .andExpect(jsonPath("$.recentHistory[0].id").value(10))
                    .andExpect(jsonPath("$.recentHistory[0].officeName").value("東京事務所"))
                    .andExpect(jsonPath("$.recentHistory[0].userName").value("山田太郎"))
                    .andExpect(jsonPath("$.recentHistory[0].sendType").value("PLAN"))
                    // sentAt フィールドが null でなく存在すること
                    .andExpect(jsonPath("$.recentHistory[0].sentAt").exists())
                    .andExpect(jsonPath("$.recentHistory[0].sentAt").isNotEmpty());
        }

        /**
         * TC-DASH-02: 全データなし（初期状態）
         * 全カウントが 0、overdueMonths / recentHistory が空配列（null でない）であること
         * currentMonth フィールドが null でなく "yyyy-MM" 形式で返ること
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-02: データなし - カウントが0・リストが空配列・currentMonth が有効な形式で返される")
        void should_returnZeroCountsAndEmptyLists_when_noDataExists() throws Exception {
            // デフォルトスタブ（@BeforeEach で 0 / emptyList 設定済み）がそのまま使われる

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.pendingCount").value(0))
                    .andExpect(jsonPath("$.overdueCount").value(0))
                    .andExpect(jsonPath("$.sentThisMonthCount").value(0))
                    // currentMonth が null でなく "yyyy-MM" 形式であること
                    .andExpect(jsonPath("$.currentMonth").exists())
                    .andExpect(jsonPath("$.currentMonth").isNotEmpty())
                    .andExpect(jsonPath("$.currentMonth").value(
                            LocalDate.now().format(MONTH_FORMATTER)))
                    // null ではなく空配列であること
                    .andExpect(jsonPath("$.overdueMonths").isArray())
                    .andExpect(jsonPath("$.overdueMonths.length()").value(0))
                    .andExpect(jsonPath("$.recentHistory").isArray())
                    .andExpect(jsonPath("$.recentHistory.length()").value(0));
        }

        /**
         * TC-DASH-03: 今月の PENDING のみ存在し、過去月の延滞なし
         * pendingCount > 0 でも overdueCount = 0、overdueMonths が空配列になること
         * currentMonth / sentThisMonthCount も合わせて検証
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-03: 今月のPENDINGのみ - overdueCount=0 かつ overdueMonths が空・currentMonth が正常")
        void should_returnZeroOverdue_when_pendingExistsOnlyInCurrentMonth() throws Exception {
            LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

            when(mailSendRepository.countByStatus(SendStatus.PENDING)).thenReturn(2L);
            // countGroupedByMonthBefore は空（今月より前の延滞なし）→ デフォルトスタブのまま

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.pendingCount").value(2))
                    .andExpect(jsonPath("$.overdueCount").value(0))
                    .andExpect(jsonPath("$.sentThisMonthCount").value(0))
                    .andExpect(jsonPath("$.currentMonth").value(thisMonth.format(MONTH_FORMATTER)))
                    .andExpect(jsonPath("$.overdueMonths").isArray())
                    .andExpect(jsonPath("$.overdueMonths.length()").value(0));
        }

        /**
         * TC-DASH-04: 送付済みレコードが多数あっても recentHistory は最大 5 件
         * サービスが PageRequest.of(0, 5) をリポジトリに渡していることを verify で確認する
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-04: 送付履歴多数 - recentHistory が 5 件で返され PageRequest(0,5) が使われる")
        void should_returnFiveItems_when_recentHistoryHasManyRecords() throws Exception {
            LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

            // buildRecentHistoryProjections を when().thenReturn() の外側で先に実行する
            List<MailSendRepository.RecentSentHistoryProjection> fiveItems = buildRecentHistoryProjections(5);

            when(mailSendRepository.countByStatusAndSendMonth(SendStatus.SENT, thisMonth)).thenReturn(20L);
            when(mailSendRepository.findRecentSentHistory(eq(SendStatus.SENT), any(Pageable.class)))
                    .thenReturn(fiveItems);

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.recentHistory").isArray())
                    .andExpect(jsonPath("$.recentHistory.length()").value(5))
                    .andExpect(jsonPath("$.recentHistory[0].id").value(1))
                    .andExpect(jsonPath("$.recentHistory[0].officeName").value("事務所1"))
                    .andExpect(jsonPath("$.recentHistory[4].id").value(5))
                    .andExpect(jsonPath("$.recentHistory[4].officeName").value("事務所5"));

            // サービスが内部で PageRequest.of(0, 5) を使っていることを検証
            verify(mailSendRepository).findRecentSentHistory(
                    eq(SendStatus.SENT),
                    eq(PageRequest.of(0, 5)));
        }

        /**
         * TC-DASH-05: overdueCount が overdueMonths の count 合計と一致すること
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-05: overdueCount が overdueMonths の合計と一致する")
        void should_matchOverdueCountWithSumOfOverdueMonths() throws Exception {
            LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

            MailSendRepository.OverdueMonthProjection m1 = mock(MailSendRepository.OverdueMonthProjection.class);
            when(m1.getSendMonth()).thenReturn(LocalDate.of(2026, 3, 1));
            when(m1.getCount()).thenReturn(3L);

            MailSendRepository.OverdueMonthProjection m2 = mock(MailSendRepository.OverdueMonthProjection.class);
            when(m2.getSendMonth()).thenReturn(LocalDate.of(2026, 4, 1));
            when(m2.getCount()).thenReturn(2L);

            when(mailSendRepository.countByStatus(SendStatus.PENDING)).thenReturn(7L);
            when(mailSendRepository.countGroupedByMonthBefore(eq(SendStatus.PENDING), eq(thisMonth)))
                    .thenReturn(List.of(m1, m2));

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    // overdueCount == m1.count + m2.count == 5
                    .andExpect(jsonPath("$.overdueCount").value(5))
                    .andExpect(jsonPath("$.overdueMonths.length()").value(2))
                    .andExpect(jsonPath("$.overdueMonths[0].count").value(3))
                    .andExpect(jsonPath("$.overdueMonths[1].count").value(2));
        }

        /**
         * TC-DASH-08: sendType が MONITORING のケース
         * PLAN だけでなく MONITORING も正しく JSON にシリアライズされることを確認する
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-08: sendType=MONITORING - レスポンスに \"MONITORING\" が返される")
        void should_returnMonitoringSendType_when_sendTypeIsMonitoring() throws Exception {
            MailSendRepository.RecentSentHistoryProjection history =
                    mock(MailSendRepository.RecentSentHistoryProjection.class);
            when(history.getId()).thenReturn(20L);
            when(history.getOfficeName()).thenReturn("大阪事務所");
            when(history.getUserName()).thenReturn("鈴木花子");
            when(history.getSendType()).thenReturn(SendType.MONITORING);
            when(history.getSentAt()).thenReturn(LocalDateTime.of(2026, 5, 30, 9, 0));

            when(mailSendRepository.findRecentSentHistory(eq(SendStatus.SENT), any(Pageable.class)))
                    .thenReturn(List.of(history));

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.recentHistory.length()").value(1))
                    .andExpect(jsonPath("$.recentHistory[0].id").value(20))
                    .andExpect(jsonPath("$.recentHistory[0].officeName").value("大阪事務所"))
                    .andExpect(jsonPath("$.recentHistory[0].userName").value("鈴木花子"))
                    // sendType が文字列 "MONITORING" として返ること
                    .andExpect(jsonPath("$.recentHistory[0].sendType").value("MONITORING"));
        }

        /**
         * TC-DASH-09: sentAt フィールドが存在し null でないこと（専用テスト）
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-09: sentAt フィールドが存在し null でない")
        void should_includeSentAtField_when_recentHistoryExists() throws Exception {
            MailSendRepository.RecentSentHistoryProjection history =
                    mock(MailSendRepository.RecentSentHistoryProjection.class);
            when(history.getId()).thenReturn(30L);
            when(history.getOfficeName()).thenReturn("名古屋事務所");
            when(history.getUserName()).thenReturn("田中一郎");
            when(history.getSendType()).thenReturn(SendType.PLAN);
            when(history.getSentAt()).thenReturn(LocalDateTime.of(2026, 6, 1, 15, 30, 0));

            when(mailSendRepository.findRecentSentHistory(eq(SendStatus.SENT), any(Pageable.class)))
                    .thenReturn(List.of(history));

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    // sentAt フィールドが存在し、null でないこと
                    .andExpect(jsonPath("$.recentHistory[0].sentAt").exists())
                    .andExpect(jsonPath("$.recentHistory[0].sentAt").isNotEmpty());
        }

        /**
         * TC-DASH-10: recentHistory の並び順（sentAt 降順）をサービスが維持すること
         * リポジトリが返した降順リストを、サービスが並び替えを破壊しないことを確認する
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-10: sentAt 降順リストをサービスが順序を維持して返す")
        void should_preserveSentAtDescOrder_when_repositoryReturnsSortedList() throws Exception {
            MailSendRepository.RecentSentHistoryProjection newerHistory =
                    mock(MailSendRepository.RecentSentHistoryProjection.class);
            when(newerHistory.getId()).thenReturn(100L);
            when(newerHistory.getOfficeName()).thenReturn("新しい事務所");
            when(newerHistory.getUserName()).thenReturn("新しいユーザー");
            when(newerHistory.getSendType()).thenReturn(SendType.PLAN);
            when(newerHistory.getSentAt()).thenReturn(LocalDateTime.of(2026, 6, 2, 10, 0));

            MailSendRepository.RecentSentHistoryProjection olderHistory =
                    mock(MailSendRepository.RecentSentHistoryProjection.class);
            when(olderHistory.getId()).thenReturn(99L);
            when(olderHistory.getOfficeName()).thenReturn("古い事務所");
            when(olderHistory.getUserName()).thenReturn("古いユーザー");
            when(olderHistory.getSendType()).thenReturn(SendType.PLAN);
            when(olderHistory.getSentAt()).thenReturn(LocalDateTime.of(2026, 5, 15, 9, 0));

            // リポジトリは降順（newer → older）で返す
            when(mailSendRepository.findRecentSentHistory(eq(SendStatus.SENT), any(Pageable.class)))
                    .thenReturn(List.of(newerHistory, olderHistory));

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.recentHistory.length()").value(2))
                    // 先頭が新しいレコード（id=100）であること
                    .andExpect(jsonPath("$.recentHistory[0].id").value(100))
                    .andExpect(jsonPath("$.recentHistory[0].officeName").value("新しい事務所"))
                    // 2番目が古いレコード（id=99）であること
                    .andExpect(jsonPath("$.recentHistory[1].id").value(99))
                    .andExpect(jsonPath("$.recentHistory[1].officeName").value("古い事務所"));
        }

        /**
         * TC-DASH-11: overdueMonths が月昇順に並んでいること（3件で検証）
         * overdueCount が合計と一致することも合わせて確認する
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-11: overdueMonths が月昇順で返され、overdueCount と合計が一致する")
        void should_returnOverdueMonthsInAscendingOrder() throws Exception {
            LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

            MailSendRepository.OverdueMonthProjection jan = mock(MailSendRepository.OverdueMonthProjection.class);
            when(jan.getSendMonth()).thenReturn(LocalDate.of(2026, 1, 1));
            when(jan.getCount()).thenReturn(2L);

            MailSendRepository.OverdueMonthProjection feb = mock(MailSendRepository.OverdueMonthProjection.class);
            when(feb.getSendMonth()).thenReturn(LocalDate.of(2026, 2, 1));
            when(feb.getCount()).thenReturn(1L);

            MailSendRepository.OverdueMonthProjection mar = mock(MailSendRepository.OverdueMonthProjection.class);
            when(mar.getSendMonth()).thenReturn(LocalDate.of(2026, 3, 1));
            when(mar.getCount()).thenReturn(3L);

            when(mailSendRepository.countGroupedByMonthBefore(eq(SendStatus.PENDING), eq(thisMonth)))
                    .thenReturn(List.of(jan, feb, mar));

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.overdueMonths.length()").value(3))
                    .andExpect(jsonPath("$.overdueMonths[0].month").value("2026-01"))
                    .andExpect(jsonPath("$.overdueMonths[0].count").value(2))
                    .andExpect(jsonPath("$.overdueMonths[1].month").value("2026-02"))
                    .andExpect(jsonPath("$.overdueMonths[1].count").value(1))
                    .andExpect(jsonPath("$.overdueMonths[2].month").value("2026-03"))
                    .andExpect(jsonPath("$.overdueMonths[2].count").value(3))
                    // overdueCount が合計（2+1+3=6）と一致すること
                    .andExpect(jsonPath("$.overdueCount").value(6));
        }

        /**
         * TC-DASH-12: currentMonth が "yyyy-MM" 形式の正規表現にマッチすること
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-12: currentMonth が \"yyyy-MM\" 形式にマッチする")
        void should_returnCurrentMonthInYearMonthFormat() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.currentMonth",
                            matchesPattern("\\d{4}-\\d{2}")));
        }

        /**
         * TC-DASH-13: recentHistory の各項目が id / officeName / userName / sendType / sentAt
         * の全フィールドを持つこと（フィールド欠落がないことの完全確認）
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-13: recentHistory 各項目が全フィールド（id/officeName/userName/sendType/sentAt）を持つ")
        void should_includeAllRequiredFields_in_recentHistoryItem() throws Exception {
            MailSendRepository.RecentSentHistoryProjection history =
                    mock(MailSendRepository.RecentSentHistoryProjection.class);
            when(history.getId()).thenReturn(50L);
            when(history.getOfficeName()).thenReturn("札幌事務所");
            when(history.getUserName()).thenReturn("北田次郎");
            when(history.getSendType()).thenReturn(SendType.MONITORING);
            when(history.getSentAt()).thenReturn(LocalDateTime.of(2026, 6, 3, 8, 0));

            when(mailSendRepository.findRecentSentHistory(eq(SendStatus.SENT), any(Pageable.class)))
                    .thenReturn(List.of(history));

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.recentHistory[0].id").value(50))
                    .andExpect(jsonPath("$.recentHistory[0].officeName").value("札幌事務所"))
                    .andExpect(jsonPath("$.recentHistory[0].userName").value("北田次郎"))
                    .andExpect(jsonPath("$.recentHistory[0].sendType").value("MONITORING"))
                    .andExpect(jsonPath("$.recentHistory[0].sentAt").exists())
                    .andExpect(jsonPath("$.recentHistory[0].sentAt").isNotEmpty());
        }

        /**
         * TC-DASH-14: recentHistory が 1 件のみのケース（境界値）
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-14: 送付履歴が1件のみ - 境界値で正しく返される")
        void should_returnOneItem_when_recentHistoryHasExactlyOneRecord() throws Exception {
            LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);

            List<MailSendRepository.RecentSentHistoryProjection> oneItem = buildRecentHistoryProjections(1);

            when(mailSendRepository.countByStatusAndSendMonth(SendStatus.SENT, thisMonth)).thenReturn(1L);
            when(mailSendRepository.findRecentSentHistory(eq(SendStatus.SENT), any(Pageable.class)))
                    .thenReturn(oneItem);

            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.sentThisMonthCount").value(1))
                    .andExpect(jsonPath("$.recentHistory").isArray())
                    .andExpect(jsonPath("$.recentHistory.length()").value(1))
                    .andExpect(jsonPath("$.recentHistory[0].id").value(1))
                    .andExpect(jsonPath("$.recentHistory[0].officeName").value("事務所1"))
                    .andExpect(jsonPath("$.recentHistory[0].sendType").value("PLAN"));
        }

        /**
         * TC-DASH-15: 完全な空状態で全フィールドを一括確認
         * currentMonth の存在を含む全フィールドが正しく返ること
         */
        @Test
        @WithMockUser
        @DisplayName("TC-DASH-15: 完全な空状態 - 全数値が0、全リストが空配列、currentMonth が存在する")
        void should_returnAllZerosAndEmptyLists_and_currentMonthPresent_when_completelyEmpty()
                throws Exception {
            // デフォルトスタブで全て 0 / empty が返るため追加設定不要
            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.pendingCount").value(0))
                    .andExpect(jsonPath("$.overdueCount").value(0))
                    .andExpect(jsonPath("$.sentThisMonthCount").value(0))
                    // currentMonth が null でなく "yyyy-MM" 形式
                    .andExpect(jsonPath("$.currentMonth").exists())
                    .andExpect(jsonPath("$.currentMonth").isNotEmpty())
                    .andExpect(jsonPath("$.currentMonth",
                            matchesPattern("\\d{4}-\\d{2}")))
                    // リストが null でなく空配列
                    .andExpect(jsonPath("$.overdueMonths").isArray())
                    .andExpect(jsonPath("$.overdueMonths.length()").value(0))
                    .andExpect(jsonPath("$.recentHistory").isArray())
                    .andExpect(jsonPath("$.recentHistory.length()").value(0));
        }
    }

    // ============================================================
    // GET /api/dashboard — 認証・認可
    // ============================================================
    @Nested
    @DisplayName("GET /api/dashboard — 認証・認可")
    class AuthTests {

        /**
         * TC-DASH-06: 未認証（セッションなし）でアクセス → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-DASH-06: 未認証アクセス - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(DASHBOARD_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-DASH-07: ログアウト後にアクセス → 401 Unauthorized（セッション無効化の確認）
         * 実際のログイン→ログアウトフローを通してセッションが正しく破棄されることを検証する
         */
        @Test
        @DisplayName("TC-DASH-07: ログアウト後のアクセス - 401 Unauthorized")
        void should_return401_when_accessedAfterLogout() throws Exception {
            // Arrange: ログインしてセッションを取得
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", LOGIN_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isOk())
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

            // ログアウト
            mockMvc.perform(post("/api/auth/logout").session(session))
                    .andExpect(status().isOk());

            // Act & Assert: 無効化されたセッションでダッシュボードにアクセス
            mockMvc.perform(get(DASHBOARD_URL).session(session))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // ヘルパーメソッド
    // ============================================================

    /**
     * 指定件数分の RecentSentHistoryProjection モックリストを生成する。
     * id=i, officeName="事務所i", userName="利用者i", sendType=PLAN, sentAt=2026-06-01 10:i:00
     *
     * 注意: Mockito の when().thenReturn() は Stream の遅延評価と組み合わせると
     * UnfinishedStubbingException が発生するため、for ループで逐次生成する。
     */
    private List<MailSendRepository.RecentSentHistoryProjection> buildRecentHistoryProjections(int count) {
        List<MailSendRepository.RecentSentHistoryProjection> result = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            MailSendRepository.RecentSentHistoryProjection projection =
                    mock(MailSendRepository.RecentSentHistoryProjection.class);
            when(projection.getId()).thenReturn((long) i);
            when(projection.getOfficeName()).thenReturn("事務所" + i);
            when(projection.getUserName()).thenReturn("利用者" + i);
            when(projection.getSendType()).thenReturn(SendType.PLAN);
            when(projection.getSentAt()).thenReturn(LocalDateTime.of(2026, 6, 1, 10, i, 0));
            result.add(projection);
        }
        return result;
    }
}
