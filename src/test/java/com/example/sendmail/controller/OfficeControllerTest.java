package com.example.sendmail.controller;

import com.example.sendmail.dto.request.CreateOfficeRequest;
import com.example.sendmail.dto.request.UpdateOfficeRequest;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.service.OfficeService;
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
 * OfficeController の統合テスト。
 *
 * 設計方針:
 *   - @SpringBootTest で実際の SecurityFilterChain を通す
 *   - OfficeService を @MockitoBean でモックし、DB 接続なしにインメモリで完結
 *   - @WithMockUser で認証状態を表現する
 *   - /api/offices/** は SecurityConfig で authenticated() のみ（ロール制限なし、ADMIN/STAFFどちらも可）
 *   - テスト ID は TC-OFFICE-01 から連番
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("OfficeController 統合テスト")
class OfficeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OfficeService officeService;

    private static final String BASE_URL = "/api/offices";

    private OfficeResponse activeOfficeResponse;
    private OfficeResponse inactiveOfficeResponse;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 7, 10, 0, 0);

        activeOfficeResponse = OfficeResponse.builder()
                .id(1L)
                .name("中央事業所")
                .postalCode("100-0001")
                .building("中央ビル3F")
                .address("東京都千代田区1-1-1")
                .phone("03-1234-5678")
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        inactiveOfficeResponse = OfficeResponse.builder()
                .id(2L)
                .name("廃止事業所")
                .postalCode("200-0002")
                .building(null)
                .address("神奈川県横浜市2-2-2")
                .phone("045-9876-5432")
                .isActive(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ============================================================
    // GET /api/offices
    // ============================================================
    @Nested
    @DisplayName("GET /api/offices — 事業所一覧取得")
    class ListOffices {

        /**
         * TC-OFFICE-01: 認証済みユーザーがアクティブ事業所一覧を取得 → 200 OK
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-OFFICE-01: 認証済み - includeInactive=false で200 OK")
        void should_return200_when_listingActiveOffices() throws Exception {
            when(officeService.listOffices(false)).thenReturn(List.of(activeOfficeResponse));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("中央事業所"))
                    .andExpect(jsonPath("$[0].isActive").value(true));
        }

        /**
         * TC-OFFICE-02: includeInactive=true で全事業所取得 → 200 OK（非アクティブ含む）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-02: includeInactive=true で200 OK（全事業所）")
        void should_return200_and_allOffices_when_includeInactiveTrue() throws Exception {
            when(officeService.listOffices(true)).thenReturn(List.of(activeOfficeResponse, inactiveOfficeResponse));

            mockMvc.perform(get(BASE_URL).param("includeInactive", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].isActive").value(true))
                    .andExpect(jsonPath("$[1].isActive").value(false));
        }

        /**
         * TC-OFFICE-03: 未認証でアクセス → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-OFFICE-03: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-OFFICE-04: 事業所が0件 → 200 OK + 空配列
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-OFFICE-04: 事業所が0件 - 200 OK + 空配列")
        void should_return200_and_emptyList_when_noOfficeExists() throws Exception {
            when(officeService.listOffices(false)).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ============================================================
    // POST /api/offices
    // ============================================================
    @Nested
    @DisplayName("POST /api/offices — 事業所作成")
    class CreateOffice {

        /**
         * TC-OFFICE-05: 有効なリクエストで事業所を作成 → 201 Created + OfficeResponse
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-05: 正常系 - 201 Created とOfficeResponseを返す")
        void should_return201_when_creatingValidOffice() throws Exception {
            when(officeService.createOffice(any(CreateOfficeRequest.class))).thenReturn(activeOfficeResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "中央事業所",
                                      "postalCode": "100-0001",
                                      "building": "中央ビル3F",
                                      "address": "東京都千代田区1-1-1",
                                      "phone": "03-1234-5678"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("中央事業所"))
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        /**
         * TC-OFFICE-06: name が blank → 400（@NotBlank バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-06: nameがblank - 400 Bad Request（@NotBlank）")
        void should_return400_when_nameIsBlank() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "",
                                      "address": "東京都千代田区1-1-1"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-OFFICE-07: name が省略 → 400（@NotBlank バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-07: nameが省略 - 400 Bad Request（@NotBlank）")
        void should_return400_when_nameIsMissing() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "address": "東京都千代田区1-1-1"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-OFFICE-08: name が201文字（max=200違反） → 400（@Size バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-08: nameが201文字 - 400 Bad Request（@Size max=200）")
        void should_return400_when_nameIsTooLong() throws Exception {
            String longName = "あ".repeat(201);
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "%s"
                                    }
                                    """.formatted(longName)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-OFFICE-09: postalCode が9文字（max=8違反） → 400（@Size バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-09: postalCodeが9文字 - 400 Bad Request（@Size max=8）")
        void should_return400_when_postalCodeIsTooLong() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "中央事業所",
                                      "postalCode": "123456789"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.postalCode").exists());
        }

        /**
         * TC-OFFICE-10: 任意項目（postalCode等）が省略 → 201 Created
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-10: 任意項目省略 - 201 Created")
        void should_return201_when_optionalFieldsAreOmitted() throws Exception {
            when(officeService.createOffice(any(CreateOfficeRequest.class))).thenReturn(activeOfficeResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "中央事業所"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1));
        }

        /**
         * TC-OFFICE-11: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-OFFICE-11: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "中央事業所"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // GET /api/offices/{id}
    // ============================================================
    @Nested
    @DisplayName("GET /api/offices/{id} — 事業所詳細取得")
    class GetOffice {

        /**
         * TC-OFFICE-12: 存在するIDで事業所詳細を取得 → 200 OK
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-OFFICE-12: 正常系 - 200 OK + OfficeResponse")
        void should_return200_when_officeExists() throws Exception {
            when(officeService.getOffice(1L)).thenReturn(activeOfficeResponse);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("中央事業所"));
        }

        /**
         * TC-OFFICE-13: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-OFFICE-13: 存在しないID - 404 Not Found")
        void should_return404_when_officeNotFound() throws Exception {
            when(officeService.getOffice(999L))
                    .thenThrow(new ResourceNotFoundException("事業所が見つかりません: 999"));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("事業所が見つかりません: 999"));
        }

        /**
         * TC-OFFICE-14: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-OFFICE-14: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // PUT /api/offices/{id}
    // ============================================================
    @Nested
    @DisplayName("PUT /api/offices/{id} — 事業所更新")
    class UpdateOffice {

        /**
         * TC-OFFICE-15: 有効なリクエストで事業所を更新 → 200 OK
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-15: 正常系 - 200 OK")
        void should_return200_when_updatingValidOffice() throws Exception {
            OfficeResponse updated = OfficeResponse.builder()
                    .id(1L)
                    .name("中央事業所（移転）")
                    .postalCode("100-0002")
                    .address("東京都千代田区2-2-2")
                    .isActive(true)
                    .createdAt(LocalDateTime.of(2026, 6, 7, 10, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 6, 7, 12, 0, 0))
                    .build();
            when(officeService.updateOffice(eq(1L), any(UpdateOfficeRequest.class))).thenReturn(updated);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "中央事業所（移転）",
                                      "postalCode": "100-0002",
                                      "address": "東京都千代田区2-2-2"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("中央事業所（移転）"))
                    .andExpect(jsonPath("$.address").value("東京都千代田区2-2-2"));
        }

        /**
         * TC-OFFICE-16: name が blank → 400（@NotBlank バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-16: nameがblank - 400 Bad Request（@NotBlank）")
        void should_return400_when_nameIsBlank() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-OFFICE-17: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-17: 存在しないID - 404 Not Found")
        void should_return404_when_officeNotFound() throws Exception {
            when(officeService.updateOffice(eq(999L), any(UpdateOfficeRequest.class)))
                    .thenThrow(new ResourceNotFoundException("事業所が見つかりません: 999"));

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "中央事業所"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("事業所が見つかりません: 999"));
        }

        /**
         * TC-OFFICE-18: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-OFFICE-18: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "中央事業所"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // DELETE /api/offices/{id} — 事業所無効化（論理削除）
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/offices/{id} — 事業所無効化")
    class DeactivateOffice {

        /**
         * TC-OFFICE-19: 事業所を無効化 → 204 No Content
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-19: 正常系 - 204 No Content")
        void should_return204_when_deactivatingOffice() throws Exception {
            doNothing().when(officeService).deactivateOffice(1L);

            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isNoContent());

            verify(officeService).deactivateOffice(1L);
        }

        /**
         * TC-OFFICE-20: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-20: 存在しないID - 404 Not Found")
        void should_return404_when_officeNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("事業所が見つかりません: 999"))
                    .when(officeService).deactivateOffice(999L);

            mockMvc.perform(delete(BASE_URL + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("事業所が見つかりません: 999"));
        }

        /**
         * TC-OFFICE-21: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-OFFICE-21: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // PATCH /api/offices/{id}/activate
    // ============================================================
    @Nested
    @DisplayName("PATCH /api/offices/{id}/activate — 事業所有効化")
    class ActivateOffice {

        /**
         * TC-OFFICE-22: 事業所を有効化 → 200 OK + OfficeResponse
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-22: 正常系 - 200 OK")
        void should_return200_when_activatingOffice() throws Exception {
            OfficeResponse activated = OfficeResponse.builder()
                    .id(2L)
                    .name("廃止事業所")
                    .isActive(true)
                    .createdAt(LocalDateTime.of(2026, 6, 7, 10, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 6, 7, 12, 0, 0))
                    .build();
            when(officeService.activateOffice(2L)).thenReturn(activated);

            mockMvc.perform(patch(BASE_URL + "/2/activate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(2))
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        /**
         * TC-OFFICE-23: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-OFFICE-23: 存在しないID - 404 Not Found")
        void should_return404_when_officeNotFound() throws Exception {
            when(officeService.activateOffice(999L))
                    .thenThrow(new ResourceNotFoundException("事業所が見つかりません: 999"));

            mockMvc.perform(patch(BASE_URL + "/999/activate"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("事業所が見つかりません: 999"));
        }

        /**
         * TC-OFFICE-24: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-OFFICE-24: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/2/activate"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }
}
