package com.example.sendmail.controller;

import com.example.sendmail.dto.request.CreateStaffRequest;
import com.example.sendmail.dto.request.UpdateStaffRequest;
import com.example.sendmail.dto.response.StaffResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.service.StaffService;
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
 * StaffController の統合テスト。
 *
 * 設計方針:
 *   - @SpringBootTest で実際の SecurityFilterChain を通す
 *   - StaffService を @MockitoBean でモックし、DB 接続なしにインメモリで完結
 *   - @WithMockUser で認証状態を表現する
 *   - /api/staffs/** は SecurityConfig で ADMIN ロールのみ許可
 *   - テスト ID は TC-STAFF-01 から連番
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("StaffController 統合テスト")
class StaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffService staffService;

    // ---------- テストフィクスチャ ----------

    private static final String BASE_URL = "/api/staffs";

    private StaffResponse activeStaffResponse;
    private StaffResponse inactiveStaffResponse;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 4, 10, 0, 0);

        activeStaffResponse = StaffResponse.builder()
                .id(1L)
                .name("田中一郎")
                .email("tanaka@example.com")
                .roleId(1L)
                .role("ADMIN")
                .isActive(true)
                .forcePasswordChange(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        inactiveStaffResponse = StaffResponse.builder()
                .id(2L)
                .name("鈴木二郎")
                .email("suzuki@example.com")
                .roleId(2L)
                .role("STAFF")
                .isActive(false)
                .forcePasswordChange(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ============================================================
    // GET /api/staffs
    // ============================================================
    @Nested
    @DisplayName("GET /api/staffs — スタッフ一覧取得")
    class ListStaffs {

        /**
         * TC-STAFF-01: ADMINがアクティブスタッフ一覧を取得
         * → 200 OK + アクティブスタッフのみ返る
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-01: 認証済みADMIN - includeInactive=false で200 OK")
        void should_return200_when_adminListsActiveStaffs() throws Exception {
            when(staffService.listStaffs(false)).thenReturn(List.of(activeStaffResponse));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("田中一郎"))
                    .andExpect(jsonPath("$[0].email").value("tanaka@example.com"))
                    .andExpect(jsonPath("$[0].role").value("ADMIN"))
                    .andExpect(jsonPath("$[0].isActive").value(true));
        }

        /**
         * TC-STAFF-02: ADMINがincludeInactive=true で全スタッフ取得
         * → 200 OK（非アクティブ含む）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-02: 認証済みADMIN - includeInactive=true で200 OK（全スタッフ）")
        void should_return200_and_allStaffs_when_adminListsWithIncludeInactive() throws Exception {
            when(staffService.listStaffs(true)).thenReturn(List.of(activeStaffResponse, inactiveStaffResponse));

            mockMvc.perform(get(BASE_URL).param("includeInactive", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].isActive").value(true))
                    .andExpect(jsonPath("$[1].isActive").value(false));
        }

        /**
         * TC-STAFF-03: 未認証でアクセス → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-STAFF-03: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-STAFF-04: STAFFロールでアクセス → 403 Forbidden
         * /api/staffs/** は SecurityConfig で ADMIN のみ許可
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-STAFF-04: STAFFロール - 403 Forbidden")
        void should_return403_when_staffRoleAccesses() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }

        /**
         * TC-STAFF-05: スタッフが0件 → 200 OK + 空配列
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-05: スタッフが0件 - 200 OK + 空配列")
        void should_return200_and_emptyList_when_noStaffExists() throws Exception {
            when(staffService.listStaffs(false)).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ============================================================
    // POST /api/staffs
    // ============================================================
    @Nested
    @DisplayName("POST /api/staffs — スタッフ作成")
    class CreateStaff {

        /**
         * TC-STAFF-06: ADMINが有効なリクエストでスタッフを作成 → 201 Created + StaffResponse
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-06: 正常系 - 201 Created とStaffResponseを返す")
        void should_return201_when_adminCreatesValidStaff() throws Exception {
            when(staffService.createStaff(any(CreateStaffRequest.class))).thenReturn(activeStaffResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "email": "tanaka@example.com",
                                      "password": "password1",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("田中一郎"))
                    .andExpect(jsonPath("$.email").value("tanaka@example.com"))
                    .andExpect(jsonPath("$.role").value("ADMIN"))
                    .andExpect(jsonPath("$.isActive").value(true))
                    .andExpect(jsonPath("$.forcePasswordChange").value(true));
        }

        /**
         * TC-STAFF-07: name が blank → 400（バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-07: nameがblank - 400 Bad Request（@NotBlank）")
        void should_return400_when_nameIsBlank() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "",
                                      "email": "tanaka@example.com",
                                      "password": "password1",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-STAFF-08: email が不正形式 → 400（バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-08: emailが不正形式 - 400 Bad Request（@Email）")
        void should_return400_when_emailIsInvalid() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "email": "not-an-email",
                                      "password": "password1",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.email").exists());
        }

        /**
         * TC-STAFF-09: password が7文字（min=8違反）→ 400
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-09: passwordが7文字 - 400 Bad Request（@Size min=8）")
        void should_return400_when_passwordIsTooShort() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "email": "tanaka@example.com",
                                      "password": "1234567",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        /**
         * TC-STAFF-10: password が null/省略 → 400（@NotBlank バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-10: passwordがnull/省略 - 400 Bad Request（@NotBlank）")
        void should_return400_when_passwordIsNull() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "email": "tanaka@example.com",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        /**
         * TC-STAFF-11: role が blank → 400（バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-11: roleがblank - 400 Bad Request（@NotBlank）")
        void should_return400_when_roleIsBlank() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "email": "tanaka@example.com",
                                      "password": "password1",
                                      "role": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.role").exists());
        }

        /**
         * TC-STAFF-12: 重複メール → 409 Conflict
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-12: 重複メール - 409 Conflict")
        void should_return409_when_emailIsDuplicated() throws Exception {
            when(staffService.createStaff(any(CreateStaffRequest.class)))
                    .thenThrow(new DuplicateResourceException("このメールアドレスは既に使用されています: tanaka@example.com"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "email": "tanaka@example.com",
                                      "password": "password1",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("このメールアドレスは既に使用されています: tanaka@example.com"));
        }

        /**
         * TC-STAFF-13: STAFFロールで実行 → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-STAFF-13: STAFFロール - 403 Forbidden")
        void should_return403_when_staffTriesToCreate() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "email": "tanaka@example.com",
                                      "password": "password1",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }

        /**
         * TC-STAFF-14: 未認証で実行 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-STAFF-14: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "email": "tanaka@example.com",
                                      "password": "password1",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // PUT /api/staffs/{id}
    // ============================================================
    @Nested
    @DisplayName("PUT /api/staffs/{id} — スタッフ更新")
    class UpdateStaff {

        /**
         * TC-STAFF-15: ADMINが有効なリクエストでスタッフを更新 → 200 OK
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-15: 正常系 - 全フィールド更新で200 OK")
        void should_return200_when_adminUpdatesStaff() throws Exception {
            StaffResponse updatedResponse = StaffResponse.builder()
                    .id(1L)
                    .name("田中一郎（更新）")
                    .email("tanaka@example.com")
                    .roleId(2L)
                    .role("STAFF")
                    .isActive(true)
                    .forcePasswordChange(true)
                    .createdAt(LocalDateTime.of(2026, 6, 4, 10, 0, 0))
                    .updatedAt(LocalDateTime.of(2026, 6, 4, 12, 0, 0))
                    .build();
            when(staffService.updateStaff(eq(1L), any(UpdateStaffRequest.class))).thenReturn(updatedResponse);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎（更新）",
                                      "role": "STAFF",
                                      "password": "newpass1"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("田中一郎（更新）"))
                    .andExpect(jsonPath("$.role").value("STAFF"));
        }

        /**
         * TC-STAFF-16: password を省略（変更なし）→ 200 OK
         * UpdateStaffRequest.password は @Size のみ（@NotBlank なし）なので省略可能
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-16: password省略（変更なし）- 200 OK")
        void should_return200_when_passwordIsOmitted() throws Exception {
            when(staffService.updateStaff(eq(1L), any(UpdateStaffRequest.class))).thenReturn(activeStaffResponse);

            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));

            verify(staffService).updateStaff(eq(1L), any(UpdateStaffRequest.class));
        }

        /**
         * TC-STAFF-17: password が7文字 → 400（@Size min=8 バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-17: passwordが7文字 - 400 Bad Request（@Size min=8）")
        void should_return400_when_passwordIsTooShort() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "role": "ADMIN",
                                      "password": "1234567"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.password").exists());
        }

        /**
         * TC-STAFF-18: name が blank → 400（@NotBlank バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-18: nameがblank - 400 Bad Request（@NotBlank）")
        void should_return400_when_nameIsBlank() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-STAFF-19: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-19: 存在しないID - 404 Not Found")
        void should_return404_when_staffNotFound() throws Exception {
            when(staffService.updateStaff(eq(999L), any(UpdateStaffRequest.class)))
                    .thenThrow(new ResourceNotFoundException("スタッフが見つかりません: 999"));

            mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("スタッフが見つかりません: 999"));
        }

        /**
         * TC-STAFF-20: 非アクティブスタッフの更新 → 404 Not Found
         * findActiveStaffById が非アクティブの場合に ResourceNotFoundException をスロー
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-20: 非アクティブスタッフの更新 - 404 Not Found")
        void should_return404_when_staffIsInactive() throws Exception {
            when(staffService.updateStaff(eq(2L), any(UpdateStaffRequest.class)))
                    .thenThrow(new ResourceNotFoundException("スタッフが見つかりません: 2"));

            mockMvc.perform(put(BASE_URL + "/2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "鈴木二郎",
                                      "role": "STAFF"
                                    }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("スタッフが見つかりません: 2"));
        }

        /**
         * TC-STAFF-21: STAFFロールで実行 → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-STAFF-21: STAFFロール - 403 Forbidden")
        void should_return403_when_staffRoleTriesToUpdate() throws Exception {
            mockMvc.perform(put(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "田中一郎",
                                      "role": "ADMIN"
                                    }
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }

    // ============================================================
    // PATCH /api/staffs/{id}/activate
    // ============================================================
    @Nested
    @DisplayName("PATCH /api/staffs/{id}/activate — スタッフ有効化")
    class ActivateStaff {

        /**
         * TC-STAFF-22: ADMINがスタッフを有効化 → 204 No Content
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-22: 正常系 - 204 No Content")
        void should_return204_when_adminActivatesStaff() throws Exception {
            doNothing().when(staffService).activateStaff(2L);

            mockMvc.perform(patch(BASE_URL + "/2/activate"))
                    .andExpect(status().isNoContent());

            verify(staffService).activateStaff(2L);
        }

        /**
         * TC-STAFF-23: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-23: 存在しないID - 404 Not Found")
        void should_return404_when_staffNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("スタッフが見つかりません: 999"))
                    .when(staffService).activateStaff(999L);

            mockMvc.perform(patch(BASE_URL + "/999/activate"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("スタッフが見つかりません: 999"));
        }

        /**
         * TC-STAFF-24: STAFFロールで実行 → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-STAFF-24: STAFFロール - 403 Forbidden")
        void should_return403_when_staffRoleTriesToActivate() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/2/activate"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }

    // ============================================================
    // DELETE /api/staffs/{id} — スタッフ無効化（論理削除）
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/staffs/{id} — スタッフ無効化")
    class DeactivateStaff {

        /**
         * TC-STAFF-25: ADMINがスタッフを無効化 → 204 No Content
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-25: 正常系 - 204 No Content")
        void should_return204_when_adminDeactivatesStaff() throws Exception {
            doNothing().when(staffService).deactivateStaff(2L);

            mockMvc.perform(delete(BASE_URL + "/2"))
                    .andExpect(status().isNoContent());

            verify(staffService).deactivateStaff(2L);
        }

        /**
         * TC-STAFF-26: 存在しないID → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-26: 存在しないID - 404 Not Found")
        void should_return404_when_staffNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("スタッフが見つかりません: 999"))
                    .when(staffService).deactivateStaff(999L);

            mockMvc.perform(delete(BASE_URL + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("スタッフが見つかりません: 999"));
        }

        /**
         * TC-STAFF-27: 自分自身の無効化 → 400 Bad Request
         * StaffService.deactivateStaff が SecurityContext の認証ユーザーと一致する場合に
         * IllegalArgumentException をスロー → GlobalExceptionHandler が 400 を返す
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-STAFF-27: 自分自身の無効化 - 400 Bad Request")
        void should_return400_when_adminTriesToDeactivateThemselves() throws Exception {
            doThrow(new IllegalArgumentException("自分自身を無効化することはできません"))
                    .when(staffService).deactivateStaff(1L);

            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("自分自身を無効化することはできません"));
        }

        /**
         * TC-STAFF-28: STAFFロールで実行 → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-STAFF-28: STAFFロール - 403 Forbidden")
        void should_return403_when_staffRoleTriesToDeactivate() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/2"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }
}
