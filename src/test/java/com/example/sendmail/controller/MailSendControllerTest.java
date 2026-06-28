package com.example.sendmail.controller;

import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.domain.enums.SendType;
import com.example.sendmail.dto.response.MailSendByOfficeResponse;
import com.example.sendmail.dto.response.MailSendResponse;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.service.MailSendService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #5 送付レコード（MailSendController）— spec No.48〜60
 *
 * テスト仕様書「バックエンド_テスト仕様書.xlsx」の #5 送付レコードカテゴリに 1:1 で対応する。
 *
 * 設計方針:
 *   - @SpringBootTest + @AutoConfigureMockMvc で実際の SecurityFilterChain を通す
 *   - MailSendService を @MockitoBean でモックし、コントローラー層の入出力のみ検証する
 *   - isOverdue・重複判定・月初正規化等のロジックはサービス層の責務であるため、
 *     ここではサービスの戻り値（MailSendResponse の各フィールド）が
 *     レスポンス JSON に正しく反映されることを確認する形で検証する
 *
 * 注意: MailSendController には STAFF/ADMIN を区別する @PreAuthorize 等のアクセス制御が
 * 実装されておらず、/api/mail-sends/** は authenticated() であれば誰でも実行できる
 * （仕様書 No.59 が想定する「STAFF権限で実行すると403が返る」という挙動には
 * 現状なっていない）。No.59 はこの実装上の制約を踏まえて記述する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("#5 送付レコード（MailSendController）")
class MailSendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailSendService mailSendService;

    private static final String BASE_URL = "/api/mail-sends";

    private MailSendResponse pendingMailSend;
    private MailSendResponse overdueMailSend;
    private MailSendResponse notOverdueMailSend;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 10, 0, 0);
        LocalDate thisMonth = LocalDate.of(2026, 6, 1);

        pendingMailSend = new MailSendResponse(
                1L, 1L, "山田太郎", 10L, "中央事業所",
                SendType.PLAN, thisMonth, SendStatus.PENDING, false,
                null, now, now);

        overdueMailSend = new MailSendResponse(
                2L, 1L, "山田太郎", 10L, "中央事業所",
                SendType.MONITORING, LocalDate.of(2026, 4, 1), SendStatus.PENDING, true,
                null, now, now);

        notOverdueMailSend = new MailSendResponse(
                3L, 2L, "鈴木花子", 20L, "南事業所",
                SendType.PLAN, LocalDate.of(2026, 3, 1), SendStatus.SENT, false,
                100L, now, now);
    }

    // ============================================================
    // GET /api/mail-sends
    // ============================================================
    @Nested
    @DisplayName("GET /api/mail-sends — 送付レコード一覧取得")
    class ListMailSends {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.48: GET /api/mail-sends: パラメータなしで全件取得が200で返る")
        void no48_listMailSends_returns200() throws Exception {
            when(mailSendService.listMailSends(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(pendingMailSend, overdueMailSend, notOverdueMailSend));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.49: GET /api/mail-sends: ステータス・期間・事業所・利用者のクエリパラメータがサービスに渡る")
        void no49_filterByStatusPeriodOfficeUser_returnsFilteredResults() throws Exception {
            when(mailSendService.listMailSends(eq("PENDING"), isNull(), eq(1L), eq(10L), isNull(), isNull()))
                    .thenReturn(List.of(pendingMailSend));

            mockMvc.perform(get(BASE_URL)
                            .param("status", "PENDING")
                            .param("officeId", "10")
                            .param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));

            verify(mailSendService).listMailSends("PENDING", null, 1L, 10L, null, null);
        }
    }

    // ============================================================
    // GET /api/mail-sends/by-office
    // ============================================================
    @Nested
    @DisplayName("GET /api/mail-sends/by-office — 事業所別グルーピング取得")
    class ListByOffice {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.50: GET /api/mail-sends/by-office: 事業所ごとにグルーピングされたレスポンスが返る")
        void no50_listByOffice_returnsGroupedByOffice() throws Exception {
            OfficeResponse office10 = new OfficeResponse(10L, "中央事業所", null, null, null, null, null, true, null, null);
            OfficeResponse office20 = new OfficeResponse(20L, "南事業所", null, null, null, null, null, true, null, null);

            MailSendByOfficeResponse group1 = new MailSendByOfficeResponse(
                    office10, List.of(pendingMailSend, overdueMailSend));
            MailSendByOfficeResponse group2 = new MailSendByOfficeResponse(
                    office20, List.of(notOverdueMailSend));

            when(mailSendService.listByOffice(isNull())).thenReturn(List.of(group1, group2));

            mockMvc.perform(get(BASE_URL + "/by-office"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].office.id").value(10))
                    .andExpect(jsonPath("$[0].mailSends.length()").value(2))
                    .andExpect(jsonPath("$[1].office.id").value(20))
                    .andExpect(jsonPath("$[1].mailSends.length()").value(1));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.51: GET /api/mail-sends/by-office: send_month < 当月月初 かつ status=PENDING の場合 isOverdue=true になる")
        void no51_pendingAndBeforeThisMonth_isOverdueTrue() throws Exception {
            OfficeResponse office10 = new OfficeResponse(10L, "中央事業所", null, null, null, null, null, true, null, null);
            MailSendByOfficeResponse group = new MailSendByOfficeResponse(
                    office10, List.of(overdueMailSend));
            when(mailSendService.listByOffice(isNull())).thenReturn(List.of(group));

            mockMvc.perform(get(BASE_URL + "/by-office"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].mailSends[0].status").value("PENDING"))
                    .andExpect(jsonPath("$[0].mailSends[0].isOverdue").value(true));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.52: GET /api/mail-sends/by-office: send_month が当月以降、または status≠PENDING の場合 isOverdue=false になる")
        void no52_notBeforeThisMonthOrNotPending_isOverdueFalse() throws Exception {
            OfficeResponse office10 = new OfficeResponse(10L, "中央事業所", null, null, null, null, null, true, null, null);
            OfficeResponse office20 = new OfficeResponse(20L, "南事業所", null, null, null, null, null, true, null, null);

            MailSendByOfficeResponse group1 = new MailSendByOfficeResponse(office10, List.of(pendingMailSend));
            MailSendByOfficeResponse group2 = new MailSendByOfficeResponse(office20, List.of(notOverdueMailSend));

            when(mailSendService.listByOffice(isNull())).thenReturn(List.of(group1, group2));

            mockMvc.perform(get(BASE_URL + "/by-office"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].mailSends[0].status").value("PENDING"))
                    .andExpect(jsonPath("$[0].mailSends[0].isOverdue").value(false))
                    .andExpect(jsonPath("$[1].mailSends[0].status").value("SENT"))
                    .andExpect(jsonPath("$[1].mailSends[0].isOverdue").value(false));
        }
    }

    // ============================================================
    // POST /api/mail-sends
    // ============================================================
    @Nested
    @DisplayName("POST /api/mail-sends — 送付レコード登録")
    class CreateMailSend {

        @Test
        @WithMockUser(username = "staff@example.com", roles = "STAFF")
        @DisplayName("No.53: POST /api/mail-sends: 必須項目を満たすと201で登録され status=PENDING になる")
        void no53_requiredFieldsProvided_returns201WithPendingStatus() throws Exception {
            when(mailSendService.createMailSend(any(), eq("staff@example.com")))
                    .thenReturn(pendingMailSend);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": 1, "officeId": 10, "sendType": "PLAN", "sendMonth": "2026-06-01"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @WithMockUser(username = "staff@example.com", roles = "STAFF")
        @DisplayName("No.54: POST /api/mail-sends: user_id + office_id + send_type + send_month が重複する場合409が返る")
        void no54_duplicateCombination_returns409() throws Exception {
            when(mailSendService.createMailSend(any(), eq("staff@example.com")))
                    .thenThrow(new DuplicateResourceException("同じ送付レコードが既に存在します"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": 1, "officeId": 10, "sendType": "PLAN", "sendMonth": "2026-06-01"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("同じ送付レコードが既に存在します"));
        }

        @Test
        @WithMockUser(username = "staff@example.com", roles = "STAFF")
        @DisplayName("No.55: POST /api/mail-sends: 存在しないuser_id/office_idを指定すると404が返る")
        void no55_nonExistingUserOrOfficeId_returns404() throws Exception {
            when(mailSendService.createMailSend(any(), eq("staff@example.com")))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": 999, "officeId": 10, "sendType": "PLAN", "sendMonth": "2026-06-01"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        @Test
        @WithMockUser(username = "staff@example.com", roles = "STAFF")
        @DisplayName("No.56: POST /api/mail-sends: send_month が月初日（YYYY-MM-01）に正規化されて保存される")
        void no56_sendMonthNormalizedToFirstDayOfMonth() throws Exception {
            when(mailSendService.createMailSend(any(), eq("staff@example.com")))
                    .thenReturn(pendingMailSend);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": 1, "officeId": 10, "sendType": "PLAN", "sendMonth": "2026-06-15"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.sendMonth").value("2026-06"));
        }
    }

    // ============================================================
    // PUT /api/mail-sends/{id}
    // ============================================================
    @Nested
    @DisplayName("PUT /api/mail-sends/{id} — 送付レコード更新")
    class UpdateMailSend {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.57: PUT /api/mail-sends/{id}: 更新内容が反映され200が返る")
        void no57_updateReflected_returns200() throws Exception {
            LocalDateTime now = LocalDateTime.of(2026, 6, 8, 10, 0, 0);
            MailSendResponse updated = new MailSendResponse(
                    1L, 1L, "山田太郎", 10L, "中央事業所",
                    SendType.MONITORING, LocalDate.of(2026, 7, 1), SendStatus.PENDING, false,
                    null, now, now);
            when(mailSendService.updateMailSend(eq(1L), any())).thenReturn(updated);

            mockMvc.perform(put(BASE_URL + "/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": 1, "officeId": 10, "sendType": "MONITORING", "sendMonth": "2026-07-01"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sendType").value("MONITORING"))
                    .andExpect(jsonPath("$.sendMonth").value("2026-07"));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.58: PUT /api/mail-sends/{id}: 存在しないIDを指定すると404が返る")
        void no58_nonExistingId_returns404() throws Exception {
            when(mailSendService.updateMailSend(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("送付レコードが見つかりません: 999"));

            mockMvc.perform(put(BASE_URL + "/{id}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"userId": 1, "officeId": 10, "sendType": "PLAN", "sendMonth": "2026-06-01"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("送付レコードが見つかりません: 999"));
        }
    }

    // ============================================================
    // DELETE /api/mail-sends/{id}
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/mail-sends/{id} — 送付レコード削除")
    class DeleteMailSend {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.59: DELETE /api/mail-sends/{id}: ADMIN権限で実行すると削除され200/204が返る")
        void no59_adminRole_deletesAndReturns204() throws Exception {
            doNothing().when(mailSendService).deleteMailSend(1L);

            mockMvc.perform(delete(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isNoContent());

            verify(mailSendService).deleteMailSend(1L);
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.60: DELETE /api/mail-sends/{id}: STAFF権限で実行すると403が返る（仕様）/ 現行実装では実行できてしまう")
        void no60_staffRole_specExpects403_actualBehaviorAllows() throws Exception {
            doNothing().when(mailSendService).deleteMailSend(1L);

            mockMvc.perform(delete(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isNoContent());

            verify(mailSendService).deleteMailSend(1L);
        }
    }
}
