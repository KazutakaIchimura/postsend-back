package com.example.sendmail.controller;

import com.example.sendmail.dto.request.AddOfficeToUserRequest;
import com.example.sendmail.dto.request.CreateUserRequest;
import com.example.sendmail.dto.request.UpdateUserRequest;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.dto.response.UserResponse;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController の統合テスト。
 *
 * 設計方針:
 *   - @SpringBootTest で実際の SecurityFilterChain を通す
 *   - UserService を @MockitoBean でモックし、DB 接続なしにインメモリで完結
 *   - @WithMockUser で認証状態を表現する
 *   - テスト ID は TC-USER-01 から連番
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("UserController 統合テスト")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // ---------- テストフィクスチャ ----------

    private static final String BASE_URL = "/api/users";

    private UserResponse activeUserResponse;
    private UserResponse inactiveUserResponse;
    private UserResponse userWithOfficesResponse;
    private OfficeResponse officeResponse1;
    private OfficeResponse officeResponse2;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 4, 10, 0, 0);

        activeUserResponse = UserResponse.builder()
                .id(1L)
                .name("山田太郎")
                .nameKana("ヤマダタロウ")
                .birthDate(LocalDate.of(1980, 4, 1))
                .notes("備考テスト")
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        inactiveUserResponse = UserResponse.builder()
                .id(2L)
                .name("鈴木花子")
                .nameKana("スズキハナコ")
                .birthDate(LocalDate.of(1990, 8, 15))
                .notes(null)
                .isActive(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        officeResponse1 = OfficeResponse.builder()
                .id(10L)
                .name("東京事務所")
                .build();

        officeResponse2 = OfficeResponse.builder()
                .id(20L)
                .name("大阪事務所")
                .build();

        userWithOfficesResponse = UserResponse.builder()
                .id(1L)
                .name("山田太郎")
                .nameKana("ヤマダタロウ")
                .birthDate(LocalDate.of(1980, 4, 1))
                .notes("備考テスト")
                .isActive(true)
                .createdAt(now)
                .updatedAt(now)
                .offices(List.of(officeResponse1, officeResponse2))
                .build();
    }

    // ============================================================
    // GET /api/users
    // ============================================================
    @Nested
    @DisplayName("GET /api/users — 利用者一覧取得")
    class ListUsers {

        /**
         * TC-USER-01: 認証済みユーザーが includeInactive=false で一覧取得
         * → 200 OK + アクティブ利用者のみ返る
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-01: 認証済みSTAFF - includeInactive=false で200 OK")
        void should_return200_when_authenticatedStaffListsActiveUsers() throws Exception {
            when(userService.listUsers(false)).thenReturn(List.of(activeUserResponse));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("山田太郎"))
                    .andExpect(jsonPath("$[0].isActive").value(true));
        }

        /**
         * TC-USER-02: 認証済みADMINが includeInactive=true で一覧取得
         * → 200 OK + アクティブ・非アクティブ両方の利用者が返る
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-02: 認証済みADMIN - includeInactive=true で200 OK（全利用者）")
        void should_return200_and_allUsers_when_adminListsWithIncludeInactive() throws Exception {
            when(userService.listUsers(true)).thenReturn(List.of(activeUserResponse, inactiveUserResponse));

            mockMvc.perform(get(BASE_URL).param("includeInactive", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].isActive").value(true))
                    .andExpect(jsonPath("$[1].isActive").value(false));
        }

        /**
         * TC-USER-03: STAFFロールで includeInactive=true → 403 Forbidden
         * コントローラー内で Authentication のロールを検査し AccessDeniedException をスローする
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-03: STAFFロールで includeInactive=true - 403 Forbidden")
        void should_return403_when_staffRequestsIncludeInactive() throws Exception {
            mockMvc.perform(get(BASE_URL).param("includeInactive", "true"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }

        /**
         * TC-USER-04: 未認証で一覧取得 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-04: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-USER-05: 利用者が0件の場合 → 200 OK + 空配列
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-05: 利用者が0件 - 200 OK + 空配列")
        void should_return200_and_emptyList_when_noUsersExist() throws Exception {
            when(userService.listUsers(false)).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ============================================================
    // POST /api/users
    // ============================================================
    @Nested
    @DisplayName("POST /api/users — 利用者作成")
    class CreateUser {

        /**
         * TC-USER-06: ADMINが有効なリクエストで利用者を作成 → 201 Created
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-06: 正常系 - 201 Created とUserResponseを返す")
        void should_return201_when_adminCreatesValidUser() throws Exception {
            when(userService.createUser(any(CreateUserRequest.class))).thenReturn(activeUserResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "山田太郎",
                                      "nameKana": "ヤマダタロウ",
                                      "birthDate": "1980-04-01",
                                      "notes": "備考テスト"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("山田太郎"))
                    .andExpect(jsonPath("$.nameKana").value("ヤマダタロウ"))
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        /**
         * TC-USER-07: nameKana・birthDate・notes を省略した最小リクエスト → 201 Created
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-07: 必須項目のみ - 201 Created（省略可能フィールドなし）")
        void should_return201_when_onlyRequiredFieldsProvided() throws Exception {
            UserResponse minimalResponse = UserResponse.builder()
                    .id(3L)
                    .name("田中次郎")
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(userService.createUser(any(CreateUserRequest.class))).thenReturn(minimalResponse);

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "田中次郎"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.name").value("田中次郎"));
        }

        /**
         * TC-USER-08: name が null → 400 Bad Request（@NotBlank バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-08: nameがnull - 400 Bad Request（@NotBlank）")
        void should_return400_when_nameIsNull() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"nameKana": "テスト"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-USER-09: name が空文字 → 400 Bad Request（@NotBlank バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-09: nameが空文字 - 400 Bad Request（@NotBlank）")
        void should_return400_when_nameIsBlank() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": ""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-USER-10: name が 101 文字（最大 100 文字超え）→ 400 Bad Request（@Size）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-10: nameが101文字 - 400 Bad Request（@Size max=100）")
        void should_return400_when_nameExceedsMaxLength() throws Exception {
            String longName = "あ".repeat(101);
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"" + longName + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-USER-11: birthDate が未来日付 → 400 Bad Request（@Past バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-11: birthDateが未来日付 - 400 Bad Request（@Past）")
        void should_return400_when_birthDateIsFuture() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "山田太郎",
                                      "birthDate": "2099-01-01"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.birthDate").exists());
        }

        /**
         * TC-USER-12: notes が 2001 文字（最大 2000 文字超え）→ 400 Bad Request（@Size）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-12: notesが2001文字 - 400 Bad Request（@Size max=2000）")
        void should_return400_when_notesExceedsMaxLength() throws Exception {
            String longNotes = "a".repeat(2001);
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"山田太郎\", \"notes\": \"" + longNotes + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.notes").exists());
        }

        /**
         * TC-USER-13: 未認証で作成 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-13: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"山田太郎\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-USER-14: STAFFロールで作成 → 403 Forbidden（@PreAuthorize("hasRole('ADMIN')")）
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-14: STAFFロール - 403 Forbidden")
        void should_return403_when_staffTriesToCreate() throws Exception {
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"山田太郎\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }

    // ============================================================
    // GET /api/users/{id}
    // ============================================================
    @Nested
    @DisplayName("GET /api/users/{id} — 利用者取得")
    class GetUser {

        /**
         * TC-USER-15: 認証済みSTAFFが存在する利用者を取得 → 200 OK + UserResponse（offices付き）
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-15: 正常系 - 200 OK + UserResponse（offices付き）")
        void should_return200_and_userWithOffices_when_userExists() throws Exception {
            when(userService.getUser(1L)).thenReturn(userWithOfficesResponse);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("山田太郎"))
                    .andExpect(jsonPath("$.nameKana").value("ヤマダタロウ"))
                    .andExpect(jsonPath("$.isActive").value(true))
                    .andExpect(jsonPath("$.offices").isArray())
                    .andExpect(jsonPath("$.offices.length()").value(2))
                    .andExpect(jsonPath("$.offices[0].id").value(10))
                    .andExpect(jsonPath("$.offices[0].name").value("東京事務所"));
        }

        /**
         * TC-USER-16: 存在しないIDで取得 → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-16: 存在しないID - 404 Not Found")
        void should_return404_when_userNotFound() throws Exception {
            when(userService.getUser(999L))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(get(BASE_URL + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        /**
         * TC-USER-17: 未認証で取得 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-17: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-USER-18: 事業所なしの利用者を取得 → 200 OK + offices フィールドが JSON に含まれない
         * （@JsonInclude(NON_NULL) によりオフィスが null の場合はシリアライズされない）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-18: 事業所なし - 200 OK + officesフィールドが非null配列")
        void should_return200_with_emptyOffices_when_userHasNoOffices() throws Exception {
            UserResponse userNoOffices = UserResponse.builder()
                    .id(1L)
                    .name("山田太郎")
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .offices(Collections.emptyList())
                    .build();
            when(userService.getUser(1L)).thenReturn(userNoOffices);

            mockMvc.perform(get(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.offices").isArray())
                    .andExpect(jsonPath("$.offices.length()").value(0));
        }
    }

    // ============================================================
    // PATCH /api/users/{id}
    // ============================================================
    @Nested
    @DisplayName("PATCH /api/users/{id} — 利用者更新")
    class UpdateUser {

        /**
         * TC-USER-19: ADMINがname・nameKana・birthDate・notesを全て指定して更新 → 200 OK
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-19: 正常系 - 全フィールド更新で200 OK")
        void should_return200_when_adminUpdatesAllFields() throws Exception {
            UserResponse updatedResponse = UserResponse.builder()
                    .id(1L)
                    .name("山田太郎（更新）")
                    .nameKana("ヤマダタロウ")
                    .birthDate(LocalDate.of(1980, 4, 1))
                    .notes("更新済み備考")
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(userService.updateUser(eq(1L), any(UpdateUserRequest.class))).thenReturn(updatedResponse);

            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "山田太郎（更新）",
                                      "nameKana": "ヤマダタロウ",
                                      "birthDate": "1980-04-01",
                                      "notes": "更新済み備考"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("山田太郎（更新）"))
                    .andExpect(jsonPath("$.notes").value("更新済み備考"));
        }

        /**
         * TC-USER-20: notes を明示的に null 送信（Optional.empty() = クリア）→ 200 OK + notes が null
         * UpdateUserRequest.notes は @JsonSetter(nulls = Nulls.AS_EMPTY) により
         * JSON の null を Optional.empty() に変換するクリア操作として扱う
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-20: notes=null送信（クリア）- 200 OK + notesがnull")
        void should_return200_with_nullNotes_when_notesSentAsNull() throws Exception {
            UserResponse responseWithNullNotes = UserResponse.builder()
                    .id(1L)
                    .name("山田太郎")
                    .notes(null)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(userService.updateUser(eq(1L), any(UpdateUserRequest.class))).thenReturn(responseWithNullNotes);

            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "山田太郎",
                                      "notes": null
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notes").value(nullValue()));
        }

        /**
         * TC-USER-21: notes フィールドを JSON に含めない（未送信 = 変更しない）→ 200 OK
         * UpdateUserRequest.notes はデフォルト null（Optional 自体が null = フィールド未送信）
         * この場合、サービス層は notes を変更しない
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-21: notes未送信（変更なし）- 200 OK + notes変更されない")
        void should_return200_when_notesFieldIsAbsent() throws Exception {
            when(userService.updateUser(eq(1L), any(UpdateUserRequest.class))).thenReturn(activeUserResponse);

            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": "山田太郎"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));

            verify(userService).updateUser(eq(1L), any(UpdateUserRequest.class));
        }

        /**
         * TC-USER-22: notes に値を送信（Optional.of("value") = 上書き）→ 200 OK + notes が更新値
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-22: notesに値送信 - 200 OK + notes更新")
        void should_return200_with_updatedNotes_when_notesValueProvided() throws Exception {
            UserResponse updatedResponse = UserResponse.builder()
                    .id(1L)
                    .name("山田太郎")
                    .notes("新しい備考")
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(userService.updateUser(eq(1L), any(UpdateUserRequest.class))).thenReturn(updatedResponse);

            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "山田太郎",
                                      "notes": "新しい備考"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notes").value("新しい備考"));
        }

        /**
         * TC-USER-23: 全フィールド未指定（空オブジェクト）→ 400 Bad Request
         * サービスが IllegalStateException をスローし GlobalExceptionHandler が 400 を返す
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-23: 全フィールド未指定 - 400 Bad Request（更新フィールドなし）")
        void should_return400_when_noFieldsSpecified() throws Exception {
            when(userService.updateUser(eq(1L), any(UpdateUserRequest.class)))
                    .thenThrow(new IllegalStateException("更新するフィールドが指定されていません"));

            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("更新するフィールドが指定されていません"));
        }

        /**
         * TC-USER-24: name が 101 文字 → 400 Bad Request（@Size バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-24: nameが101文字 - 400 Bad Request（@Size max=100）")
        void should_return400_when_nameExceedsMaxLength() throws Exception {
            String longName = "い".repeat(101);
            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"" + longName + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        /**
         * TC-USER-25: birthDate が未来日付 → 400 Bad Request（@Past バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-25: birthDateが未来日付 - 400 Bad Request（@Past）")
        void should_return400_when_birthDateIsFuture() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"birthDate": "2099-12-31"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.birthDate").exists());
        }

        /**
         * TC-USER-26: 存在しないIDで更新 → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-26: 存在しないID - 404 Not Found")
        void should_return404_when_userNotFound() throws Exception {
            when(userService.updateUser(eq(999L), any(UpdateUserRequest.class)))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(patch(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"山田太郎\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        /**
         * TC-USER-27: 未認証で更新 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-27: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"山田太郎\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-USER-28: STAFFロールで更新 → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-28: STAFFロール - 403 Forbidden")
        void should_return403_when_staffTriesToUpdate() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"山田太郎\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }

    // ============================================================
    // DELETE /api/users/{id} — 利用者無効化
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/users/{id} — 利用者無効化")
    class DeactivateUser {

        /**
         * TC-USER-29: ADMINが存在する利用者を無効化 → 200 OK + isActive=false
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-29: 正常系 - 200 OK + isActive=false")
        void should_return200_and_deactivatedUser_when_adminDeactivates() throws Exception {
            UserResponse deactivatedResponse = UserResponse.builder()
                    .id(1L)
                    .name("山田太郎")
                    .isActive(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            when(userService.deactivateUser(1L)).thenReturn(deactivatedResponse);

            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.isActive").value(false));
        }

        /**
         * TC-USER-30: 存在しないIDで無効化 → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-30: 存在しないID - 404 Not Found")
        void should_return404_when_userNotFound() throws Exception {
            when(userService.deactivateUser(999L))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(delete(BASE_URL + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        /**
         * TC-USER-31: 未認証で無効化 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-31: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-USER-32: STAFFロールで無効化 → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-32: STAFFロール - 403 Forbidden")
        void should_return403_when_staffTriesToDeactivate() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }

    // ============================================================
    // PATCH /api/users/{id}/activate — 利用者有効化
    // ============================================================
    @Nested
    @DisplayName("PATCH /api/users/{id}/activate — 利用者有効化")
    class ActivateUser {

        /**
         * TC-USER-33: ADMINが無効状態の利用者を有効化 → 200 OK + isActive=true
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-33: 正常系 - 200 OK + isActive=true")
        void should_return200_and_activatedUser_when_adminActivates() throws Exception {
            when(userService.activateUser(2L)).thenReturn(activeUserResponse);

            mockMvc.perform(patch(BASE_URL + "/2/activate"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.isActive").value(true));
        }

        /**
         * TC-USER-34: 存在しないIDで有効化 → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-34: 存在しないID - 404 Not Found")
        void should_return404_when_userNotFound() throws Exception {
            when(userService.activateUser(999L))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(patch(BASE_URL + "/999/activate"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        /**
         * TC-USER-35: 未認証で有効化 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-35: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/2/activate"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-USER-36: STAFFロールで有効化 → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-36: STAFFロール - 403 Forbidden")
        void should_return403_when_staffTriesToActivate() throws Exception {
            mockMvc.perform(patch(BASE_URL + "/2/activate"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }

    // ============================================================
    // GET /api/users/{userId}/offices — 事業所一覧取得
    // ============================================================
    @Nested
    @DisplayName("GET /api/users/{userId}/offices — 事業所一覧取得")
    class GetUserOffices {

        /**
         * TC-USER-37: 認証済みSTAFFが事業所紐付きの利用者の事業所一覧を取得 → 200 OK + 事業所リスト
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-37: 正常系 - 200 OK + 事業所リスト")
        void should_return200_and_officeList_when_userHasOffices() throws Exception {
            when(userService.getUserOffices(1L)).thenReturn(List.of(officeResponse1, officeResponse2));

            mockMvc.perform(get(BASE_URL + "/1/offices"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(10))
                    .andExpect(jsonPath("$[0].name").value("東京事務所"))
                    .andExpect(jsonPath("$[1].id").value(20))
                    .andExpect(jsonPath("$[1].name").value("大阪事務所"));
        }

        /**
         * TC-USER-38: 事業所なしの利用者の事業所一覧を取得 → 200 OK + 空配列
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-38: 事業所なし - 200 OK + 空配列")
        void should_return200_and_emptyList_when_userHasNoOffices() throws Exception {
            when(userService.getUserOffices(1L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL + "/1/offices"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        /**
         * TC-USER-39: 存在しないユーザーIDで取得 → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-39: 存在しないユーザーID - 404 Not Found")
        void should_return404_when_userNotFound() throws Exception {
            when(userService.getUserOffices(999L))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(get(BASE_URL + "/999/offices"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        /**
         * TC-USER-40: 未認証で事業所一覧取得 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-40: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL + "/1/offices"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // POST /api/users/{userId}/offices — 事業所紐付け
    // ============================================================
    @Nested
    @DisplayName("POST /api/users/{userId}/offices — 事業所紐付け")
    class AddOfficeToUser {

        /**
         * TC-USER-41: ADMINが有効なofficeIdで事業所紐付け → 201 Created + UserResponse（offices付き）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-41: 正常系 - 201 Created + UserResponse（offices付き）")
        void should_return201_and_userWithOffice_when_adminAddsOffice() throws Exception {
            when(userService.addOfficeToUser(eq(1L), eq(10L))).thenReturn(userWithOfficesResponse);

            mockMvc.perform(post(BASE_URL + "/1/offices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 10}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.offices").isArray())
                    .andExpect(jsonPath("$.offices.length()").value(2));
        }

        /**
         * TC-USER-42: officeId が null → 400 Bad Request（@NotNull バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-42: officeIdがnull - 400 Bad Request（@NotNull）")
        void should_return400_when_officeIdIsNull() throws Exception {
            mockMvc.perform(post(BASE_URL + "/1/offices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": null}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.officeId").exists());
        }

        /**
         * TC-USER-43: officeId が 0（正の整数でない）→ 400 Bad Request（@Positive バリデーション）
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-43: officeIdが0 - 400 Bad Request（@Positive）")
        void should_return400_when_officeIdIsZero() throws Exception {
            mockMvc.perform(post(BASE_URL + "/1/offices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 0}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.officeId").exists());
        }

        /**
         * TC-USER-44: 存在しないユーザーIDで紐付け → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-44: 存在しないユーザーID - 404 Not Found")
        void should_return404_when_userNotFound() throws Exception {
            when(userService.addOfficeToUser(eq(999L), anyLong()))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(post(BASE_URL + "/999/offices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 10}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        /**
         * TC-USER-45: 存在しない事業所IDで紐付け → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-45: 存在しない事業所ID - 404 Not Found")
        void should_return404_when_officeNotFound() throws Exception {
            when(userService.addOfficeToUser(eq(1L), eq(999L)))
                    .thenThrow(new ResourceNotFoundException("事業所が見つかりません: 999"));

            mockMvc.perform(post(BASE_URL + "/1/offices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 999}
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("事業所が見つかりません: 999"));
        }

        /**
         * TC-USER-46: 未認証で事業所紐付け → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-46: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(post(BASE_URL + "/1/offices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 10}
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-USER-47: STAFFロールで事業所紐付け → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-47: STAFFロール - 403 Forbidden")
        void should_return403_when_staffTriesToAddOffice() throws Exception {
            mockMvc.perform(post(BASE_URL + "/1/offices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 10}
                                    """))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }

        /**
         * TC-USER-48: 重複する事業所紐付け → 409 Conflict
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-48: 重複紐付け - 409 Conflict")
        void should_return409_when_officeAlreadyLinked() throws Exception {
            when(userService.addOfficeToUser(eq(1L), eq(10L)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            mockMvc.perform(post(BASE_URL + "/1/offices")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 10}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("既に登録済みのデータです"));
        }
    }

    // ============================================================
    // DELETE /api/users/{userId}/offices/{officeId} — 事業所紐付け解除
    // ============================================================
    @Nested
    @DisplayName("DELETE /api/users/{userId}/offices/{officeId} — 事業所紐付け解除")
    class RemoveOfficeFromUser {

        /**
         * TC-USER-48: ADMINが紐付けを解除 → 204 No Content
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-48: 正常系 - 204 No Content")
        void should_return204_when_adminRemovesOffice() throws Exception {
            doNothing().when(userService).removeOfficeFromUser(1L, 10L);

            mockMvc.perform(delete(BASE_URL + "/1/offices/10"))
                    .andExpect(status().isNoContent());

            verify(userService).removeOfficeFromUser(1L, 10L);
        }

        /**
         * TC-USER-49: 存在しないユーザーIDで解除 → 404 Not Found
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-49: 存在しないユーザーID - 404 Not Found")
        void should_return404_when_userNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("利用者が見つかりません: 999"))
                    .when(userService).removeOfficeFromUser(999L, 10L);

            mockMvc.perform(delete(BASE_URL + "/999/offices/10"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        /**
         * TC-USER-50: 存在しない紐付けを解除 → 404 Not Found
         * userOfficeRepository.findByUserIdAndOfficeId が空を返し ResourceNotFoundException がスローされる
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-USER-50: 存在しない紐付け - 404 Not Found")
        void should_return404_when_associationNotFound() throws Exception {
            doThrow(new ResourceNotFoundException("紐付けが見つかりません"))
                    .when(userService).removeOfficeFromUser(1L, 999L);

            mockMvc.perform(delete(BASE_URL + "/1/offices/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("紐付けが見つかりません"));
        }

        /**
         * TC-USER-51: 未認証で紐付け解除 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-USER-51: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1/offices/10"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-USER-52: STAFFロールで紐付け解除 → 403 Forbidden
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-USER-52: STAFFロール - 403 Forbidden")
        void should_return403_when_staffTriesToRemoveOffice() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/1/offices/10"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }
}
