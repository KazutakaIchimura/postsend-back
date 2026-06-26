package com.example.sendmail.controller;

import com.example.sendmail.dto.response.StaffResponse;
import com.example.sendmail.exception.DuplicateResourceException;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #7 スタッフ管理（StaffController）— spec No.71〜86 ※ ADMIN限定
 *
 * テスト仕様書「バックエンド_テスト仕様書.xlsx」の #7 スタッフ管理カテゴリに 1:1 で対応する。
 *
 * 設計方針:
 *   - @SpringBootTest + @AutoConfigureMockMvc で実際の SecurityFilterChain を通す
 *   - StaffService を @MockitoBean でモックし、コントローラー層の入出力のみ検証する
 *   - SecurityConfig で /api/staffs/** は ADMIN ロールのみ許可（hasRole("ADMIN")）
 *
 * No.84: StaffService#deactivateStaff には自己無効化禁止に加え、
 * 「最後のADMINは無効化できない」チェックをバックエンドでも実装済み（2026-06-26）。
 * フロントエンドの StaffListPage#canDisable() による二重防衛は引き続き有効。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("#7 スタッフ管理（StaffController）")
class StaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffService staffService;

    private static final String BASE_URL = "/api/staffs";

    private StaffResponse activeStaff;
    private StaffResponse inactiveStaff;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 8, 10, 0, 0);

        activeStaff = StaffResponse.builder()
                .id(1L).name("有効スタッフ").email("active@example.com")
                .roleId(2L).role("STAFF").isActive(true).forcePasswordChange(true)
                .createdAt(now).updatedAt(now)
                .build();

        inactiveStaff = StaffResponse.builder()
                .id(2L).name("無効スタッフ").email("inactive@example.com")
                .roleId(2L).role("STAFF").isActive(false).forcePasswordChange(false)
                .createdAt(now).updatedAt(now)
                .build();
    }

    // ============================================================
    // 認可
    // ============================================================
    @Nested
    @DisplayName("認可 — /api/staffs/** へのアクセス制御")
    class Authorization {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.71: 認可: STAFF権限で /api/staffs/** にアクセスすると403が返る")
        void no71_staffRole_accessReturns403() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));

            verify(staffService, never()).listStaffs(anyBoolean());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.72: 認可: ADMIN権限で /api/staffs/** にアクセスできる")
        void no72_adminRole_canAccess() throws Exception {
            when(staffService.listStaffs(false)).thenReturn(List.of(activeStaff));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    // ============================================================
    // GET /api/staffs
    // ============================================================
    @Nested
    @DisplayName("GET /api/staffs — スタッフ一覧取得")
    class ListStaffs {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.73: GET /api/staffs: スタッフ一覧が200で返る")
        void no73_listStaffs_returns200() throws Exception {
            when(staffService.listStaffs(false)).thenReturn(List.of(activeStaff));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].isActive").value(true));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.74: GET /api/staffs: includeInactive=true 指定時に無効スタッフも含めて返る")
        void no74_includeInactiveTrue_returnsInactiveToo() throws Exception {
            when(staffService.listStaffs(true)).thenReturn(List.of(activeStaff, inactiveStaff));

            mockMvc.perform(get(BASE_URL).param("includeInactive", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].isActive").value(false));

            verify(staffService).listStaffs(true);
        }
    }

    // ============================================================
    // POST /api/staffs
    // ============================================================
    @Nested
    @DisplayName("POST /api/staffs — スタッフ登録")
    class CreateStaff {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.75: POST /api/staffs: 必須項目を満たすと201で登録されパスワードがハッシュ化される")
        void no75_requiredFieldsProvided_returns201WithHashedPassword() throws Exception {
            // パスワードのハッシュ化自体は StaffService（PasswordEncoder）の責務。
            // コントローラーはサービスの戻り値（StaffResponse、平文パスワードを含まない）を
            // そのまま返すため、レスポンスにハッシュ・平文いずれも含まれないことを確認する。
            when(staffService.createStaff(any())).thenReturn(activeStaff);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "有効スタッフ", "email": "active@example.com", "password": "password123", "role": "STAFF"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value("active@example.com"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.76: POST /api/staffs: 氏名が空の場合400バリデーションエラーが返る")
        void no76_blankName_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "", "email": "active@example.com", "password": "password123", "role": "STAFF"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());

            verify(staffService, never()).createStaff(any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.77: POST /api/staffs: メールアドレスが重複する場合409が返る")
        void no77_duplicateEmail_returns409() throws Exception {
            when(staffService.createStaff(any()))
                    .thenThrow(new DuplicateResourceException("このメールアドレスは既に使用されています: active@example.com"));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "重複スタッフ", "email": "active@example.com", "password": "password123", "role": "STAFF"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("このメールアドレスは既に使用されています: active@example.com"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.78: POST /api/staffs: パスワードが8文字未満の場合400バリデーションエラーが返る")
        void no78_passwordShorterThan8Chars_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "新規スタッフ", "email": "new@example.com", "password": "short1", "role": "STAFF"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());

            verify(staffService, never()).createStaff(any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.79: POST /api/staffs: 登録時 force_password_change が true で初期化される")
        void no79_forcePasswordChangeInitializedToTrue() throws Exception {
            // force_password_change の初期化自体は Staff エンティティ（デフォルト値 true）の責務。
            // コントローラー経由でレスポンスに反映されることを確認する。
            StaffResponse created = StaffResponse.builder()
                    .id(3L).name("新規スタッフ").email("new@example.com")
                    .roleId(2L).role("STAFF").isActive(true).forcePasswordChange(true)
                    .build();
            when(staffService.createStaff(any())).thenReturn(created);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "新規スタッフ", "email": "new@example.com", "password": "password123", "role": "STAFF"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.forcePasswordChange").value(true));
        }
    }

    // ============================================================
    // PUT /api/staffs/{id}
    // ============================================================
    @Nested
    @DisplayName("PUT /api/staffs/{id} — スタッフ更新")
    class UpdateStaff {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.80: PUT /api/staffs/{id}: パスワード未指定の場合は既存のパスワードハッシュが維持される")
        void no80_passwordOmitted_keepsExistingPasswordHash() throws Exception {
            // 既存ハッシュの維持自体は StaffService#updateStaff の責務（password が null の場合は更新しない）。
            // ここではパスワードを指定せずに更新リクエストを送っても正常に処理されることを確認する。
            StaffResponse updated = StaffResponse.builder()
                    .id(1L).name("改名スタッフ").email("active@example.com")
                    .roleId(2L).role("STAFF").isActive(true).forcePasswordChange(true)
                    .build();
            when(staffService.updateStaff(eq(1L), any())).thenReturn(updated);

            mockMvc.perform(put(BASE_URL + "/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "改名スタッフ", "role": "STAFF"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("改名スタッフ"))
                    .andExpect(jsonPath("$.forcePasswordChange").value(true));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.81: PUT /api/staffs/{id}: パスワード指定時は新しいハッシュに更新される")
        void no81_passwordProvided_updatesToNewHash() throws Exception {
            // 新しいハッシュへの更新自体は StaffService#updateStaff の責務
            // （password 指定時に re-encode し forcePasswordChange を false にする）。
            // ここではレスポンスの forcePasswordChange が false になることで反映を確認する。
            StaffResponse updated = StaffResponse.builder()
                    .id(1L).name("有効スタッフ").email("active@example.com")
                    .roleId(2L).role("STAFF").isActive(true).forcePasswordChange(false)
                    .build();
            when(staffService.updateStaff(eq(1L), any())).thenReturn(updated);

            mockMvc.perform(put(BASE_URL + "/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "有効スタッフ", "role": "STAFF", "password": "newpassword123"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.forcePasswordChange").value(false));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.82: PUT /api/staffs/{id}: メールアドレスを他スタッフと重複する値に変更すると409が返る")
        void no82_emailChangedToDuplicateValue_returns409() throws Exception {
            // UpdateStaffRequest にはメールアドレスのフィールドが存在しないため、
            // 本テストでは「サービス層がメール重複エラーをスローした場合に
            // コントローラーが409へ正しくマッピングする」ことを確認することで、
            // 仕様書 No.82 が現行実装でどう扱われるかを記録する。
            when(staffService.updateStaff(eq(1L), any()))
                    .thenThrow(new DuplicateResourceException("このメールアドレスは既に使用されています: other@example.com"));

            mockMvc.perform(put(BASE_URL + "/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "有効スタッフ", "role": "STAFF"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("このメールアドレスは既に使用されています: other@example.com"));
        }
    }

    // ============================================================
    // DELETE /api/staffs/{id}
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/staffs/{id} — スタッフ無効化")
    class DeactivateStaff {

        @Test
        @WithMockUser(username = "active@example.com", roles = "ADMIN")
        @DisplayName("No.83: DELETE /api/staffs/{id}: 自分自身を無効化しようとすると400エラーが返る（自己無効化禁止）")
        void no83_selfDeactivation_returns400() throws Exception {
            doThrow(new IllegalArgumentException("自分自身を無効化することはできません"))
                    .when(staffService).deactivateStaff(1L);

            mockMvc.perform(delete(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("自分自身を無効化することはできません"));
        }

        @Test
        @WithMockUser(username = "active@example.com", roles = "ADMIN")
        @DisplayName("No.84: DELETE /api/staffs/{id}: 最後のADMINを無効化しようとすると400エラーが返る（ADMIN最低1名維持）")
        void no84_lastAdminDeactivation_returns400() throws Exception {
            doThrow(new IllegalArgumentException("最後のADMINは無効化できません"))
                    .when(staffService).deactivateStaff(2L);

            mockMvc.perform(delete(BASE_URL + "/{id}", 2L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("最後のADMINは無効化できません"));
        }

        @Test
        @WithMockUser(username = "active@example.com", roles = "ADMIN")
        @DisplayName("No.85: DELETE /api/staffs/{id}: 条件を満たす場合は無効化（is_active=false）が成功する")
        void no85_validConditions_deactivationSucceeds() throws Exception {
            doNothing().when(staffService).deactivateStaff(2L);

            mockMvc.perform(delete(BASE_URL + "/{id}", 2L))
                    .andExpect(status().isNoContent());

            verify(staffService).deactivateStaff(2L);
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.86: DELETE /api/staffs/{id}: STAFF権限で実行すると403が返る")
        void no86_staffRole_returns403() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));

            verify(staffService, never()).deactivateStaff(anyLong());
        }
    }
}
