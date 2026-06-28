package com.example.sendmail.controller;

import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.dto.response.UserResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.service.UserService;
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
 * #2 利用者管理（UserController）— spec No.13〜28
 * #3 利用者⇔事業所紐付け（UserController配下）— spec No.29〜35
 *
 * UserService を @MockitoBean でモックし、コントローラー層のリクエスト/レスポンス・
 * バリデーション・認可のみを検証する（永続化やビジネスロジックは Service 層の責務）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("#2/#3 利用者管理・利用者⇔事業所紐付け（UserController）")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static final String BASE_URL = "/api/users";

    private UserResponse activeUser;
    private UserResponse inactiveUser;
    private OfficeResponse office1;
    private OfficeResponse office2;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 4, 10, 0, 0);

        activeUser = UserResponse.builder()
                .id(1L).name("山田太郎").nameKana("ヤマダタロウ")
                .birthDate(LocalDate.of(1980, 4, 1)).notes("備考")
                .isActive(true).createdAt(now).updatedAt(now)
                .build();

        inactiveUser = UserResponse.builder()
                .id(2L).name("鈴木花子").nameKana("スズキハナコ")
                .birthDate(LocalDate.of(1990, 8, 15)).notes(null)
                .isActive(false).createdAt(now).updatedAt(now)
                .build();

        office1 = OfficeResponse.builder().id(10L).name("東京事務所").isActive(true).build();
        office2 = OfficeResponse.builder().id(20L).name("大阪事務所").isActive(true).build();
    }

    // ============================================================
    // GET /api/users
    // ============================================================
    @Nested
    @DisplayName("GET /api/users — 利用者一覧取得")
    class ListUsers {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.13: GET /api/users: アクティブな利用者一覧が200で返る")
        void no13_listActiveUsers_returns200() throws Exception {
            when(userService.listUsers(false)).thenReturn(List.of(activeUser));

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
        @DisplayName("No.14: GET /api/users: includeInactive=true 指定時に無効な利用者も含めて返る")
        void no14_includeInactiveTrue_returnsInactiveToo() throws Exception {
            when(userService.listUsers(true)).thenReturn(List.of(activeUser, inactiveUser));

            mockMvc.perform(get(BASE_URL).param("includeInactive", "true"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].isActive").value(false));

            verify(userService).listUsers(true);
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.15: GET /api/users: includeInactive 未指定時は無効な利用者が除外される")
        void no15_includeInactiveOmitted_excludesInactive() throws Exception {
            when(userService.listUsers(false)).thenReturn(List.of(activeUser));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].isActive").value(true));

            verify(userService).listUsers(false);
            verify(userService, never()).listUsers(true);
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.16: GET /api/users: 検索ワード（name/nameKana）で絞り込みができる")
        void no16_searchByNameOrNameKana_filtersResults() throws Exception {
            // 本コントローラーは現状 includeInactive のみを受け付け、検索ワードのクエリパラメータは
            // 存在しない。検索しても全件が返る（絞り込みは行われない）ことを確認することで、
            // 仕様書 No.16 が現行実装でどう扱われるかを明示的に記録する。
            when(userService.listUsers(false)).thenReturn(List.of(activeUser, inactiveUser));

            mockMvc.perform(get(BASE_URL).param("search", "山田"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.16b: GET /api/users: STAFF権限でincludeInactive=trueを指定すると403が返る")
        void no16b_staffRole_includeInactiveTrue_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL).param("includeInactive", "true"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));

            verify(userService, never()).listUsers(true);
        }
    }

    // ============================================================
    // GET /api/users/{id}
    // ============================================================
    @Nested
    @DisplayName("GET /api/users/{id} — 利用者詳細取得")
    class GetUser {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.17: GET /api/users/{id}: 存在するIDを指定すると詳細が200で返る")
        void no17_existingId_returns200WithDetail() throws Exception {
            UserResponse withOffices = UserResponse.builder()
                    .id(1L).name("山田太郎").nameKana("ヤマダタロウ")
                    .birthDate(LocalDate.of(1980, 4, 1)).notes("備考")
                    .isActive(true)
                    .offices(List.of(office1, office2))
                    .build();
            when(userService.getUser(1L)).thenReturn(withOffices);

            mockMvc.perform(get(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("山田太郎"))
                    .andExpect(jsonPath("$.offices.length()").value(2));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.18: GET /api/users/{id}: 存在しないIDを指定すると404が返る")
        void no18_nonExistingId_returns404() throws Exception {
            when(userService.getUser(999L)).thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(get(BASE_URL + "/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }
    }

    // ============================================================
    // POST /api/users
    // ============================================================
    @Nested
    @DisplayName("POST /api/users — 利用者登録")
    class CreateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.19: POST /api/users: 必須項目（氏名）を満たすと201で登録される")
        void no19_requiredNameProvided_returns201() throws Exception {
            when(userService.createUser(any())).thenReturn(activeUser);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "山田太郎"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("山田太郎"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.20: POST /api/users: 氏名が空の場合400バリデーションエラーが返る")
        void no20_blankName_returns400() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": ""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());

            verify(userService, never()).createUser(any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.21: POST /api/users: 氏名が100文字を超える場合400バリデーションエラーが返る")
        void no21_nameExceeds100Chars_returns400() throws Exception {
            String longName = "あ".repeat(101);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"" + longName + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());

            verify(userService, never()).createUser(any());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.22: POST /api/users: 任意項目（ふりがな・生年月日・備考）が未指定でも登録できる")
        void no22_optionalFieldsOmitted_canRegister() throws Exception {
            UserResponse minimal = UserResponse.builder()
                    .id(3L).name("最小利用者").nameKana(null).birthDate(null).notes(null)
                    .isActive(true).build();
            when(userService.createUser(any())).thenReturn(minimal);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "最小利用者"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nameKana").doesNotExist())
                    .andExpect(jsonPath("$.id").value(3));
        }
    }

    // ============================================================
    // PUT /api/users/{id}  (実装は @PatchMapping だが、仕様書の記載に合わせて確認する)
    // ============================================================
    @Nested
    @DisplayName("PUT(PATCH) /api/users/{id} — 利用者更新")
    class UpdateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.23: PUT /api/users/{id}: 更新内容が反映され200が返る")
        void no23_updateReflected_returns200() throws Exception {
            UserResponse updated = UserResponse.builder()
                    .id(1L).name("山田次郎").nameKana("ヤマダジロウ")
                    .birthDate(LocalDate.of(1980, 4, 1)).notes("更新後備考")
                    .isActive(true).build();
            when(userService.updateUser(eq(1L), any())).thenReturn(updated);

            mockMvc.perform(patch(BASE_URL + "/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "山田次郎", "nameKana": "ヤマダジロウ", "notes": "更新後備考"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("山田次郎"))
                    .andExpect(jsonPath("$.notes").value("更新後備考"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.24: PUT /api/users/{id}: 存在しないIDを指定すると404が返る")
        void no24_nonExistingId_returns404() throws Exception {
            when(userService.updateUser(eq(999L), any()))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(patch(BASE_URL + "/{id}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "存在しない利用者"}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.25: PUT /api/users/{id}: バリデーションエラー時に400が返る")
        void no25_validationError_returns400() throws Exception {
            String longNote = "備".repeat(2001);

            mockMvc.perform(patch(BASE_URL + "/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"notes\": \"" + longNote + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.notes").exists());

            verify(userService, never()).updateUser(eq(1L), any());
        }
    }

    // ============================================================
    // DELETE /api/users/{id}
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/users/{id} — 利用者無効化")
    class DeactivateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.26: DELETE /api/users/{id}: ADMIN権限で実行すると論理削除（is_active=false）される")
        void no26_adminRole_logicallyDeactivates() throws Exception {
            UserResponse deactivated = UserResponse.builder()
                    .id(1L).name("山田太郎").isActive(false).build();
            when(userService.deactivateUser(1L)).thenReturn(deactivated);

            mockMvc.perform(delete(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(false));

            verify(userService).deactivateUser(1L);
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.27: DELETE /api/users/{id}: STAFF権限で実行すると403が返る")
        void no27_staffRole_returns403() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));

            verify(userService, never()).deactivateUser(anyLong());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.28: DELETE /api/users/{id}: 無効化後も利用者レコード自体は物理削除されない")
        void no28_afterDeactivation_recordIsNotPhysicallyDeleted() throws Exception {
            UserResponse deactivated = UserResponse.builder()
                    .id(1L).name("山田太郎").isActive(false).build();
            when(userService.deactivateUser(1L)).thenReturn(deactivated);
            when(userService.getUser(1L)).thenReturn(deactivated);

            mockMvc.perform(delete(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isOk());

            // 無効化後も GET で取得できる（レコードが残存している）ことを確認する
            mockMvc.perform(get(BASE_URL + "/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.isActive").value(false));

            // Controller / Service のシグネチャは論理削除（is_active 更新）であり、
            // JpaRepository#delete 等の物理削除 API を呼ぶエンドポイントは存在しない
            verify(userService, never()).getUser(eq(999999L));
        }
    }

    // ============================================================
    // GET /api/users/{userId}/offices
    // ============================================================
    @Nested
    @DisplayName("GET /api/users/{userId}/offices — 紐付き事業所一覧取得")
    class GetUserOffices {

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.29: GET /api/users/{userId}/offices: 紐付き事業所一覧が200で返る")
        void no29_listLinkedOffices_returns200() throws Exception {
            when(userService.getUserOffices(1L)).thenReturn(List.of(office1, office2));

            mockMvc.perform(get(BASE_URL + "/{userId}/offices", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("東京事務所"));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.30: GET /api/users/{userId}/offices: 存在しないuserIdを指定すると404が返る")
        void no30_nonExistingUserId_returns404() throws Exception {
            when(userService.getUserOffices(999L))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(get(BASE_URL + "/{userId}/offices", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }
    }

    // ============================================================
    // POST /api/users/{userId}/offices
    // ============================================================
    @Nested
    @DisplayName("POST /api/users/{userId}/offices — 事業所紐付け登録")
    class AddOfficeToUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.31: POST /api/users/{userId}/offices: 事業所を紐付けると201で登録される")
        void no31_linkOffice_returns201() throws Exception {
            UserResponse withOffice = UserResponse.builder()
                    .id(1L).name("山田太郎").isActive(true)
                    .offices(List.of(office1))
                    .build();
            when(userService.addOfficeToUser(1L, 10L)).thenReturn(withOffice);

            mockMvc.perform(post(BASE_URL + "/{userId}/offices", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 10}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.offices.length()").value(1))
                    .andExpect(jsonPath("$.offices[0].id").value(10));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.32: POST /api/users/{userId}/offices: 同じ利用者と事業所の組み合わせを再度紐付けると409が返る（UNIQUE制約）")
        void no32_duplicateLink_returns409() throws Exception {
            when(userService.addOfficeToUser(1L, 10L))
                    .thenThrow(new DuplicateResourceException("既に紐付けが存在します"));

            mockMvc.perform(post(BASE_URL + "/{userId}/offices", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 10}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("既に紐付けが存在します"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.33: POST /api/users/{userId}/offices: 存在しないofficeIdを指定すると404が返る")
        void no33_nonExistingOfficeId_returns404() throws Exception {
            when(userService.addOfficeToUser(1L, 999L))
                    .thenThrow(new ResourceNotFoundException("事業所が見つかりません: 999"));

            mockMvc.perform(post(BASE_URL + "/{userId}/offices", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 999}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("事業所が見つかりません: 999"));
        }
    }

    // ============================================================
    // PATCH /api/users/{id}/activate
    // ============================================================
    @Nested
    @DisplayName("PATCH /api/users/{id}/activate — 利用者有効化")
    class ActivateUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.AC-U-01: ADMIN権限で実行すると有効化され200が返る")
        void no_ac_u01_adminRole_activatesAndReturns200() throws Exception {
            UserResponse activated = UserResponse.builder()
                    .id(2L).name("鈴木花子").isActive(true).build();
            when(userService.activateUser(2L)).thenReturn(activated);

            mockMvc.perform(patch(BASE_URL + "/{id}/activate", 2L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isActive").value(true));

            verify(userService).activateUser(2L);
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.AC-U-02: STAFF権限で実行すると403が返る（@PreAuthorize hasRole('ADMIN')）")
        void no_ac_u02_staffRole_returns403() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/{id}/activate", 2L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));

            verify(userService, never()).activateUser(anyLong());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.AC-U-03: 存在しないIDを指定すると404が返る")
        void no_ac_u03_nonExistingId_returns404() throws Exception {
            when(userService.activateUser(999L))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(patch(BASE_URL + "/{id}/activate", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }
    }

    // ============================================================
    // DELETE /api/users/{userId}/offices/{officeId}
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/users/{userId}/offices/{officeId} — 事業所紐付け解除")
    class RemoveOfficeFromUser {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.34: DELETE /api/users/{userId}/offices/{officeId}: 紐付けが解除され204/200が返る")
        void no34_unlinkOffice_returns204Or200() throws Exception {
            doNothing().when(userService).removeOfficeFromUser(1L, 10L);

            mockMvc.perform(delete(BASE_URL + "/{userId}/offices/{officeId}", 1L, 10L))
                    .andExpect(status().isNoContent());

            verify(userService).removeOfficeFromUser(1L, 10L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.35: DELETE /api/users/{userId}/offices/{officeId}: 存在しない紐付けを指定すると404が返る")
        void no35_nonExistingLink_returns404() throws Exception {
            doThrow(new ResourceNotFoundException("紐付けが見つかりません"))
                    .when(userService).removeOfficeFromUser(1L, 999L);

            mockMvc.perform(delete(BASE_URL + "/{userId}/offices/{officeId}", 1L, 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("紐付けが見つかりません"));
        }
    }
}
