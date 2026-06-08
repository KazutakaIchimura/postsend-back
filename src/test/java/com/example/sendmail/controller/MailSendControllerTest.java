package com.example.sendmail.controller;

import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.domain.enums.SendType;
import com.example.sendmail.dto.request.CreateMailSendRequest;
import com.example.sendmail.dto.response.MailSendByOfficeResponse;
import com.example.sendmail.dto.response.MailSendResponse;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.InvalidStatusException;
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
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MailSendController の統合テスト。
 *
 * 設計方針:
 *   - @SpringBootTest で実際の SecurityFilterChain を通す
 *   - MailSendService を @MockitoBean でモックし、DB 接続なしにインメモリで完結
 *   - @WithMockUser で認証状態を表現する（username はスタッフのメールアドレスを模す）
 *   - /api/mail-sends/** は SecurityConfig で authenticated() のみ（ロール制限なし）
 *   - sendMonth はレスポンスでは "yyyy-MM" 形式（YearMonthSerializer）、リクエストでは ISO形式 "yyyy-MM-dd"
 *   - テスト ID は TC-MAILSEND-01 から連番
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("MailSendController 統合テスト")
class MailSendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailSendService mailSendService;

    private static final String BASE_URL = "/api/mail-sends";
    private static final String STAFF_EMAIL = "tanaka@example.com";

    private MailSendResponse pendingResponse;
    private MailSendResponse sentResponse;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 7, 10, 0, 0);

        pendingResponse = MailSendResponse.builder()
                .id(1L)
                .userId(10L)
                .userName("山田太郎")
                .officeId(100L)
                .officeName("中央事業所")
                .sendType(SendType.PLAN)
                .sendMonth(LocalDate.of(2026, 6, 1))
                .status(SendStatus.PENDING)
                .isOverdue(false)
                .batchId(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        sentResponse = MailSendResponse.builder()
                .id(2L)
                .userId(11L)
                .userName("佐藤花子")
                .officeId(100L)
                .officeName("中央事業所")
                .sendType(SendType.MONITORING)
                .sendMonth(LocalDate.of(2026, 5, 1))
                .status(SendStatus.SENT)
                .isOverdue(false)
                .batchId(50L)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ============================================================
    // GET /api/mail-sends
    // ============================================================
    @Nested
    @DisplayName("GET /api/mail-sends — 送付レコード一覧取得")
    class ListMailSends {

        /**
         * TC-MAILSEND-01: 認証済みユーザーが一覧を取得 → 200 OK
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-01: 正常系 - 200 OK + 一覧を返す")
        void should_return200_when_listingMailSends() throws Exception {
            when(mailSendService.listMailSends()).thenReturn(List.of(pendingResponse, sentResponse));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].userName").value("山田太郎"))
                    .andExpect(jsonPath("$[0].sendType").value("PLAN"))
                    .andExpect(jsonPath("$[0].sendMonth").value("2026-06"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"))
                    .andExpect(jsonPath("$[1].status").value("SENT"))
                    .andExpect(jsonPath("$[1].batchId").value(50));
        }

        /**
         * TC-MAILSEND-02: レコードが0件 → 200 OK + 空配列
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-02: レコードが0件 - 200 OK + 空配列")
        void should_return200_and_emptyList_when_noRecordExists() throws Exception {
            when(mailSendService.listMailSends()).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        /**
         * TC-MAILSEND-03: 未認証でアクセス → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-MAILSEND-03: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // GET /api/mail-sends/by-office
    // ============================================================
    @Nested
    @DisplayName("GET /api/mail-sends/by-office — 事業所別送付レコード一覧取得")
    class ListByOffice {

        /**
         * TC-MAILSEND-04: 認証済みユーザーが事業所別一覧を取得 → 200 OK
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-04: 正常系 - 200 OK + 事業所別グルーピング結果を返す")
        void should_return200_when_listingByOffice() throws Exception {
            OfficeResponse office = OfficeResponse.builder()
                    .id(100L)
                    .name("中央事業所")
                    .isActive(true)
                    .build();
            MailSendByOfficeResponse byOffice = MailSendByOfficeResponse.builder()
                    .office(office)
                    .mailSends(List.of(pendingResponse, sentResponse))
                    .build();
            when(mailSendService.listByOffice()).thenReturn(List.of(byOffice));

            mockMvc.perform(get(BASE_URL + "/by-office"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].office.id").value(100))
                    .andExpect(jsonPath("$[0].office.name").value("中央事業所"))
                    .andExpect(jsonPath("$[0].mailSends.length()").value(2))
                    .andExpect(jsonPath("$[0].mailSends[0].id").value(1));
        }

        /**
         * TC-MAILSEND-05: レコードが0件 → 200 OK + 空配列
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-05: 0件 - 200 OK + 空配列")
        void should_return200_and_emptyList_when_noRecordExists() throws Exception {
            when(mailSendService.listByOffice()).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL + "/by-office"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        /**
         * TC-MAILSEND-06: 未認証でアクセス → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-MAILSEND-06: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL + "/by-office"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // POST /api/mail-sends
    // ============================================================
    @Nested
    @DisplayName("POST /api/mail-sends — 送付レコード作成")
    class CreateMailSend {

        /**
         * TC-MAILSEND-07: 有効なリクエストでレコードを作成 → 201 Created
         * Authentication.getName() がそのまま service に渡される
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-07: 正常系 - 201 Created とMailSendResponseを返す")
        void should_return201_when_creatingValidMailSend() throws Exception {
            when(mailSendService.createMailSend(any(CreateMailSendRequest.class), eq(STAFF_EMAIL)))
                    .thenReturn(pendingResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.userName").value("山田太郎"))
                    .andExpect(jsonPath("$.sendType").value("PLAN"))
                    .andExpect(jsonPath("$.status").value("PENDING"));

            verify(mailSendService).createMailSend(any(CreateMailSendRequest.class), eq(STAFF_EMAIL));
        }

        /**
         * TC-MAILSEND-08: userId が null → 400（@NotNull バリデーション）
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-08: userIdがnull - 400 Bad Request（@NotNull）")
        void should_return400_when_userIdIsNull() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "officeId": 100,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.userId").exists());
        }

        /**
         * TC-MAILSEND-09: officeId が null → 400（@NotNull バリデーション）
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-09: officeIdがnull - 400 Bad Request（@NotNull）")
        void should_return400_when_officeIdIsNull() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.officeId").exists());
        }

        /**
         * TC-MAILSEND-10: sendType が不正な値 → 400（リクエストボディの形式不正）
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-10: sendTypeが不正な列挙値 - 400 Bad Request")
        void should_return400_when_sendTypeIsInvalid() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "INVALID_TYPE",
                                      "sendMonth": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("リクエストの形式が不正です"));
        }

        /**
         * TC-MAILSEND-11: sendMonth が null → 400（@NotNull バリデーション）
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-11: sendMonthが省略 - 400 Bad Request（@NotNull）")
        void should_return400_when_sendMonthIsMissing() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "PLAN"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.sendMonth").exists());
        }

        /**
         * TC-MAILSEND-12: 利用者が見つからない → 404 Not Found
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-12: 利用者が存在しない - 404 Not Found")
        void should_return404_when_userNotFound() throws Exception {
            when(mailSendService.createMailSend(any(CreateMailSendRequest.class), eq(STAFF_EMAIL)))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 999,
                                      "officeId": 100,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        /**
         * TC-MAILSEND-13: 重複する送付レコード → 409 Conflict
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-13: 重複レコード - 409 Conflict")
        void should_return409_when_duplicateRecord() throws Exception {
            when(mailSendService.createMailSend(any(CreateMailSendRequest.class), eq(STAFF_EMAIL)))
                    .thenThrow(new DuplicateResourceException("同じ送付レコードが既に存在します"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("同じ送付レコードが既に存在します"));
        }

        /**
         * TC-MAILSEND-14: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-MAILSEND-14: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-06-01"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // PUT /api/mail-sends/{id}
    // ============================================================
    @Nested
    @DisplayName("PUT /api/mail-sends/{id} — 送付レコード更新")
    class UpdateMailSend {

        /**
         * TC-MAILSEND-15: 有効なリクエストでレコードを更新 → 200 OK
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-15: 正常系 - 200 OK")
        void should_return200_when_updatingValidMailSend() throws Exception {
            MailSendResponse updated = MailSendResponse.builder()
                    .id(1L)
                    .userId(10L)
                    .userName("山田太郎")
                    .officeId(100L)
                    .officeName("中央事業所")
                    .sendType(SendType.MONITORING)
                    .sendMonth(LocalDate.of(2026, 7, 1))
                    .status(SendStatus.PENDING)
                    .isOverdue(false)
                    .createdAt(LocalDateTime.of(2026, 6, 7, 10, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 6, 7, 12, 0, 0))
                    .build();
            when(mailSendService.updateMailSend(eq(1L), any(CreateMailSendRequest.class))).thenReturn(updated);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "MONITORING",
                                      "sendMonth": "2026-07-01"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.sendType").value("MONITORING"))
                    .andExpect(jsonPath("$.sendMonth").value("2026-07"));
        }

        /**
         * TC-MAILSEND-16: 必須項目が欠落 → 400（バリデーション）
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-16: sendTypeが省略 - 400 Bad Request（@NotNull）")
        void should_return400_when_sendTypeIsMissing() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendMonth": "2026-07-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.sendType").exists());
        }

        /**
         * TC-MAILSEND-17: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-17: 存在しないID - 404 Not Found")
        void should_return404_when_mailSendNotFound() throws Exception {
            when(mailSendService.updateMailSend(eq(999L), any(CreateMailSendRequest.class)))
                    .thenThrow(new ResourceNotFoundException("送付レコードが見つかりません: 999"));

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-07-01"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("送付レコードが見つかりません: 999"));
        }

        /**
         * TC-MAILSEND-18: PENDING以外のレコードを更新 → 409 Conflict
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-18: PENDING以外のレコード - 409 Conflict（InvalidStatusException）")
        void should_return409_when_statusIsNotPending() throws Exception {
            when(mailSendService.updateMailSend(eq(2L), any(CreateMailSendRequest.class)))
                    .thenThrow(new InvalidStatusException("PENDING以外のレコードは更新できません"));

            mockMvc.perform(put(BASE_URL + "/2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-07-01"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("PENDING以外のレコードは更新できません"));
        }

        /**
         * TC-MAILSEND-19: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-MAILSEND-19: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "userId": 10,
                                      "officeId": 100,
                                      "sendType": "PLAN",
                                      "sendMonth": "2026-07-01"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // DELETE /api/mail-sends/{id}
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/mail-sends/{id} — 送付レコード削除")
    class DeleteMailSend {

        /**
         * TC-MAILSEND-20: レコードを削除 → 204 No Content
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-20: 正常系 - 204 No Content")
        void should_return204_when_deletingMailSend() throws Exception {
            doNothing().when(mailSendService).deleteMailSend(1L);

            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isNoContent());

            verify(mailSendService).deleteMailSend(1L);
        }

        /**
         * TC-MAILSEND-21: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-21: 存在しないID - 404 Not Found")
        void should_return404_when_mailSendNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("送付レコードが見つかりません: 999"))
                    .when(mailSendService).deleteMailSend(999L);

            mockMvc.perform(delete(BASE_URL + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("送付レコードが見つかりません: 999"));
        }

        /**
         * TC-MAILSEND-22: PENDING以外のレコードを削除 → 409 Conflict
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-MAILSEND-22: PENDING以外のレコード - 409 Conflict（InvalidStatusException）")
        void should_return409_when_statusIsNotPending() throws Exception {
            doThrow(new InvalidStatusException("PENDING以外のレコードは削除できません"))
                    .when(mailSendService).deleteMailSend(2L);

            mockMvc.perform(delete(BASE_URL + "/2"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("PENDING以外のレコードは削除できません"));
        }

        /**
         * TC-MAILSEND-23: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-MAILSEND-23: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }
}
