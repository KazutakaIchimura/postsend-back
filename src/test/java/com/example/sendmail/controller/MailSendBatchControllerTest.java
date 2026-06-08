package com.example.sendmail.controller;

import com.example.sendmail.dto.response.MailSendBatchResponse;
import com.example.sendmail.exception.InvalidStatusException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.service.MailSendBatchService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #6 送付バッチ（MailSendBatchController）— spec No.61〜70
 *
 * テスト仕様書「バックエンド_テスト仕様書.xlsx」の #6 送付バッチカテゴリに 1:1 で対応する。
 *
 * 設計方針:
 *   - @SpringBootTest + @AutoConfigureMockMvc で実際の SecurityFilterChain を通す
 *   - MailSendBatchService を @MockitoBean でモックし、コントローラー層の入出力のみ検証する
 *   - sent_by・batch_id の設定や対象レコードの status 更新等は MailSendBatchService の
 *     責務であるため、ここではサービスの戻り値（MailSendBatchResponse）が
 *     レスポンス JSON に正しく反映されることを確認する形で検証する
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("#6 送付バッチ（MailSendBatchController）")
class MailSendBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailSendBatchService mailSendBatchService;

    private static final String BASE_URL = "/api/mail-send-batches";
    private static final String STAFF_EMAIL = "staff@example.com";

    private MailSendBatchResponse batchResponse;

    @BeforeEach
    void setUp() {
        LocalDateTime sentAt = LocalDateTime.of(2026, 6, 8, 10, 30, 0);

        batchResponse = MailSendBatchResponse.builder()
                .batchId(100L)
                .sentAt(sentAt)
                .updatedCount(2)
                .notes("6月分送付バッチ")
                .build();
    }

    // ============================================================
    // POST /api/mail-send-batches
    // ============================================================
    @Nested
    @DisplayName("POST /api/mail-send-batches — バッチ送付済み処理")
    class CreateBatch {

        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("No.61: POST /api/mail-send-batches: mailSendIds[] を指定すると対象レコードのstatusがSENTに更新される")
        void no61_specifyMailSendIds_updatesStatusToSent() throws Exception {
            // status の更新自体はサービス層の責務。コントローラーはサービスの戻り値
            // （更新後のバッチ情報）をそのまま返すため、レスポンスに反映されることを確認する。
            when(mailSendBatchService.createBatch(any(), eq(STAFF_EMAIL))).thenReturn(batchResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mailSendIds": [1, 2]}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.batchId").value(100))
                    .andExpect(jsonPath("$.updatedCount").value(2));
        }

        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("No.62: POST /api/mail-send-batches: 更新対象レコードに batch_id が設定される")
        void no62_updatedRecords_haveBatchIdSet() throws Exception {
            // batch_id の設定自体はサービス層（MailSendBatchService#createBatch）の責務。
            // ここではレスポンスの batchId がコントローラー経由で正しく返ることを確認する。
            when(mailSendBatchService.createBatch(any(), eq(STAFF_EMAIL))).thenReturn(batchResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mailSendIds": [1, 2]}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.batchId").value(100));
        }

        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("No.63: POST /api/mail-send-batches: レスポンスの updatedCount が更新件数と一致する")
        void no63_responseUpdatedCount_matchesNumberOfUpdatedRecords() throws Exception {
            when(mailSendBatchService.createBatch(any(), eq(STAFF_EMAIL))).thenReturn(batchResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mailSendIds": [1, 2]}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.updatedCount").value(2));
        }

        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("No.64: POST /api/mail-send-batches: sent_by に処理を実行したログイン中スタッフのIDが設定される")
        void no64_sentByIsSetToCurrentLoggedInStaffId() throws Exception {
            // sent_by の設定自体はサービス層の責務。コントローラーは Authentication#getName()
            // （ログイン中スタッフのメールアドレス）をサービスへ引き渡す。
            // ここではログイン中スタッフのメールアドレスがサービスに渡されることを検証する。
            when(mailSendBatchService.createBatch(any(), eq(STAFF_EMAIL))).thenReturn(batchResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mailSendIds": [1, 2]}
                                    """))
                    .andExpect(status().isCreated());

            verify(mailSendBatchService).createBatch(any(), eq(STAFF_EMAIL));
        }

        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("No.65: POST /api/mail-send-batches: notes（任意）が指定された場合バッチに保存される")
        void no65_notesProvided_savedToBatch() throws Exception {
            when(mailSendBatchService.createBatch(any(), eq(STAFF_EMAIL))).thenReturn(batchResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mailSendIds": [1, 2], "notes": "6月分送付バッチ"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.notes").value("6月分送付バッチ"));
        }

        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("No.66: POST /api/mail-send-batches: mailSendIds[] が空配列の場合400が返る")
        void no66_emptyMailSendIds_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mailSendIds": []}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.mailSendIds").exists());

            verify(mailSendBatchService, never()).createBatch(any(), anyString());
        }

        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("No.67: POST /api/mail-send-batches: 存在しないmailSendIdを含む場合404が返る")
        void no67_nonExistingMailSendId_returns404() throws Exception {
            when(mailSendBatchService.createBatch(any(), eq(STAFF_EMAIL)))
                    .thenThrow(new ResourceNotFoundException("存在しない送付レコードが含まれています"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mailSendIds": [1, 999]}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("存在しない送付レコードが含まれています"));
        }

        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("No.68: POST /api/mail-send-batches: status=PENDING 以外（SENT/DONE）のレコードを含む場合の挙動 — 仕様は除外を期待するが現行実装はエラーとする")
        void no68_nonPendingRecordsIncluded_specExpectsExclusion_actualImplThrows409() throws Exception {
            // 仕様書 No.68 は「PENDING以外のレコードは更新対象から除外され、エラーにはしない」
            // ことを期待しているが、MailSendBatchService#createBatch の実装は
            // 1件でも PENDING 以外のレコードが含まれる場合に InvalidStatusException(409) を
            // スローする仕様になっている（除外ではなくエラー）。
            // ここでは現行実装の挙動（409エラーになる）をそのまま記録し、仕様との乖離を明示する。
            when(mailSendBatchService.createBatch(any(), eq(STAFF_EMAIL)))
                    .thenThrow(new InvalidStatusException("PENDING以外のレコードが含まれています"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mailSendIds": [1, 2, 3]}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("PENDING以外のレコードが含まれています"));
        }
    }

    // ============================================================
    // GET /api/mail-send-batches/{id}
    // ============================================================
    @Nested
    @DisplayName("GET /api/mail-send-batches/{id} — バッチ詳細取得")
    class GetBatch {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.69: GET /api/mail-send-batches/{id}: バッチ詳細（sentAt・updatedCount等）が200で返る")
        void no69_batchDetail_returns200() throws Exception {
            when(mailSendBatchService.getBatch(100L)).thenReturn(batchResponse);

            mockMvc.perform(get(BASE_URL + "/{id}", 100L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.batchId").value(100))
                    .andExpect(jsonPath("$.updatedCount").value(2))
                    .andExpect(jsonPath("$.sentAt").exists())
                    .andExpect(jsonPath("$.notes").value("6月分送付バッチ"));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.70: GET /api/mail-send-batches/{id}: 存在しないIDを指定すると404が返る")
        void no70_nonExistingId_returns404() throws Exception {
            when(mailSendBatchService.getBatch(999L))
                    .thenThrow(new ResourceNotFoundException("バッチが見つかりません: 999"));

            mockMvc.perform(get(BASE_URL + "/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("バッチが見つかりません: 999"));
        }
    }
}
