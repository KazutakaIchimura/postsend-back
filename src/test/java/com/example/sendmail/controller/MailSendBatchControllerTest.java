package com.example.sendmail.controller;

import com.example.sendmail.dto.request.CreateMailSendBatchRequest;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MailSendBatchController の統合テスト。
 *
 * 設計方針:
 *   - @SpringBootTest で実際の SecurityFilterChain を通す
 *   - MailSendBatchService を @MockitoBean でモックし、DB 接続なしにインメモリで完結
 *   - @WithMockUser で認証状態を表現する（username はスタッフのメールアドレスを模す）
 *   - /api/mail-send-batches/** は SecurityConfig で authenticated() のみ（ロール制限なし）
 *   - テスト ID は TC-BATCH-01 から連番
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("MailSendBatchController 統合テスト")
class MailSendBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MailSendBatchService mailSendBatchService;

    private static final String BASE_URL = "/api/mail-send-batches";
    private static final String STAFF_EMAIL = "tanaka@example.com";

    private MailSendBatchResponse batchResponse;

    @BeforeEach
    void setUp() {
        batchResponse = MailSendBatchResponse.builder()
                .batchId(1L)
                .sentAt(LocalDateTime.of(2026, 6, 7, 10, 0, 0))
                .updatedCount(3)
                .notes("6月分発送")
                .build();
    }

    // ============================================================
    // POST /api/mail-send-batches
    // ============================================================
    @Nested
    @DisplayName("POST /api/mail-send-batches — 一括送付バッチ作成")
    class CreateBatch {

        /**
         * TC-BATCH-01: 有効なリクエストでバッチを作成 → 201 Created
         * Authentication.getName() がそのまま service に渡される
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-01: 正常系 - 201 Created とMailSendBatchResponseを返す")
        void should_return201_when_creatingValidBatch() throws Exception {
            when(mailSendBatchService.createBatch(any(CreateMailSendBatchRequest.class), eq(STAFF_EMAIL)))
                    .thenReturn(batchResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "mailSendIds": [1, 2, 3],
                                      "notes": "6月分発送"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.batchId").value(1))
                    .andExpect(jsonPath("$.updatedCount").value(3))
                    .andExpect(jsonPath("$.notes").value("6月分発送"));

            verify(mailSendBatchService).createBatch(any(CreateMailSendBatchRequest.class), eq(STAFF_EMAIL));
        }

        /**
         * TC-BATCH-02: notes を省略 → 201 Created（notes は任意項目）
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-02: notes省略 - 201 Created")
        void should_return201_when_notesIsOmitted() throws Exception {
            MailSendBatchResponse responseWithoutNotes = MailSendBatchResponse.builder()
                    .batchId(2L)
                    .sentAt(LocalDateTime.of(2026, 6, 7, 11, 0, 0))
                    .updatedCount(1)
                    .notes(null)
                    .build();
            when(mailSendBatchService.createBatch(any(CreateMailSendBatchRequest.class), eq(STAFF_EMAIL)))
                    .thenReturn(responseWithoutNotes);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "mailSendIds": [10]
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.batchId").value(2))
                    .andExpect(jsonPath("$.updatedCount").value(1));
        }

        /**
         * TC-BATCH-03: mailSendIds が空配列 → 400（@NotEmpty バリデーション）
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-03: mailSendIdsが空配列 - 400 Bad Request（@NotEmpty）")
        void should_return400_when_mailSendIdsIsEmpty() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "mailSendIds": [],
                                      "notes": "6月分発送"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.mailSendIds").exists());
        }

        /**
         * TC-BATCH-04: mailSendIds が省略 → 400（@NotEmpty バリデーション）
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-04: mailSendIdsが省略 - 400 Bad Request（@NotEmpty）")
        void should_return400_when_mailSendIdsIsMissing() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "notes": "6月分発送"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.mailSendIds").exists());
        }

        /**
         * TC-BATCH-05: 存在しない送付レコードIDが含まれる → 404 Not Found
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-05: 存在しない送付レコードを含む - 404 Not Found")
        void should_return404_when_mailSendIdNotFound() throws Exception {
            when(mailSendBatchService.createBatch(any(CreateMailSendBatchRequest.class), eq(STAFF_EMAIL)))
                    .thenThrow(new ResourceNotFoundException("存在しない送付レコードが含まれています"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "mailSendIds": [1, 999],
                                      "notes": "6月分発送"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("存在しない送付レコードが含まれています"));
        }

        /**
         * TC-BATCH-06: PENDING以外のレコードが含まれる → 409 Conflict
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-06: PENDING以外のレコードを含む - 409 Conflict（InvalidStatusException）")
        void should_return409_when_nonPendingRecordIncluded() throws Exception {
            when(mailSendBatchService.createBatch(any(CreateMailSendBatchRequest.class), eq(STAFF_EMAIL)))
                    .thenThrow(new InvalidStatusException("PENDING以外のレコードが含まれています"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "mailSendIds": [1, 2],
                                      "notes": "6月分発送"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("PENDING以外のレコードが含まれています"));
        }

        /**
         * TC-BATCH-07: スタッフが見つからない（認証ユーザーのメールに対応するスタッフ不在） → 404 Not Found
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-07: スタッフが見つからない - 404 Not Found")
        void should_return404_when_staffNotFound() throws Exception {
            when(mailSendBatchService.createBatch(any(CreateMailSendBatchRequest.class), eq(STAFF_EMAIL)))
                    .thenThrow(new ResourceNotFoundException("スタッフが見つかりません: " + STAFF_EMAIL));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "mailSendIds": [1],
                                      "notes": "6月分発送"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("スタッフが見つかりません: " + STAFF_EMAIL));
        }

        /**
         * TC-BATCH-08: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-BATCH-08: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "mailSendIds": [1, 2],
                                      "notes": "6月分発送"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // GET /api/mail-send-batches/{id}
    // ============================================================
    @Nested
    @DisplayName("GET /api/mail-send-batches/{id} — バッチ詳細取得")
    class GetBatch {

        /**
         * TC-BATCH-09: 存在するIDでバッチ詳細を取得 → 200 OK
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-09: 正常系 - 200 OK + MailSendBatchResponse")
        void should_return200_when_batchExists() throws Exception {
            when(mailSendBatchService.getBatch(1L)).thenReturn(batchResponse);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.batchId").value(1))
                    .andExpect(jsonPath("$.updatedCount").value(3))
                    .andExpect(jsonPath("$.notes").value("6月分発送"));
        }

        /**
         * TC-BATCH-10: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(username = STAFF_EMAIL, roles = "STAFF")
        @DisplayName("TC-BATCH-10: 存在しないID - 404 Not Found")
        void should_return404_when_batchNotFound() throws Exception {
            when(mailSendBatchService.getBatch(999L))
                    .thenThrow(new ResourceNotFoundException("バッチが見つかりません: 999"));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("バッチが見つかりません: 999"));
        }

        /**
         * TC-BATCH-11: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-BATCH-11: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }
}
