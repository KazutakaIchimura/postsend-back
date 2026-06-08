package com.example.sendmail.controller;

import com.example.sendmail.domain.entity.Role;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController / Spring Security formLogin・logout の統合テスト。
 *
 * 設計方針:
 *   - @SpringBootTest で実際の SecurityFilterChain を通す
 *   - StaffRepository を @MockitoBean でモックし、DB 接続なしにインメモリで完結
 *   - BCryptPasswordEncoder は Spring Context が生成した実物を使用
 *   - 各テストは独立して実行可能（@BeforeEach で Mockito スタブを初期化）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AuthController 統合テスト")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Spring Boot 3.4 以降は @MockBean → @MockitoBean
    @MockitoBean
    private StaffRepository staffRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ---------- テストフィクスチャ ----------

    private static final String ACTIVE_EMAIL    = "active@example.com";
    private static final String INACTIVE_EMAIL  = "inactive@example.com";
    private static final String RAW_PASSWORD    = "password123";

    private Staff activeStaff;
    private Staff inactiveStaff;

    @BeforeEach
    void setUp() {
        Role role = new Role();
        role.setId(1L);
        role.setName("USER");

        activeStaff = new Staff();
        activeStaff.setId(1L);
        activeStaff.setName("有効スタッフ");
        activeStaff.setEmail(ACTIVE_EMAIL);
        activeStaff.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        activeStaff.setRole(role);
        activeStaff.setIsActive(true);
        activeStaff.setForcePasswordChange(false);

        inactiveStaff = new Staff();
        inactiveStaff.setId(2L);
        inactiveStaff.setName("無効スタッフ");
        inactiveStaff.setEmail(INACTIVE_EMAIL);
        inactiveStaff.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        inactiveStaff.setRole(role);
        inactiveStaff.setIsActive(false);
        inactiveStaff.setForcePasswordChange(false);

        // デフォルトスタブ: 存在しないメールは empty を返す
        when(staffRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(staffRepository.findByEmailIgnoreCase(ACTIVE_EMAIL)).thenReturn(Optional.of(activeStaff));
        when(staffRepository.findByEmailIgnoreCase(INACTIVE_EMAIL)).thenReturn(Optional.of(inactiveStaff));
        when(staffRepository.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ============================================================
    // POST /api/auth/login
    // ============================================================
    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        /**
         * TC-01: 有効なメール・正しいパスワード → 200 OK + セッション発行
         *        MockMvc では JSESSIONID Cookie が MockHttpServletResponse に反映されない場合があるため、
         *        レスポンスボディとセッションオブジェクトの存在で確認する
         */
        @Test
        @DisplayName("TC-01: 正常ログイン - 200 OK とセッションが生成される")
        void should_returnOk_and_session_when_validCredentials() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("ログイン成功"))
                    .andReturn();

            // セッションが生成されていることを確認
            org.junit.jupiter.api.Assertions.assertNotNull(
                    result.getRequest().getSession(false),
                    "ログイン成功後にセッションが生成されているべきです"
            );
        }

        /**
         * TC-02: 正しいメール + 誤ったパスワード → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-02: パスワード誤り - 401 Unauthorized")
        void should_return401_when_wrongPassword() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", "wrongpassword"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが違います"));
        }

        /**
         * TC-03: 存在しないメールアドレス → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-03: 存在しないメール - 401 Unauthorized")
        void should_return401_when_emailNotFound() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", "notexist@example.com")
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが違います"));
        }

        /**
         * TC-04: is_active=false のアカウント → 401 Unauthorized
         *        SecurityConfig の UserDetailsService で UsernameNotFoundException をスローする
         */
        @Test
        @DisplayName("TC-04: 無効アカウント(is_active=false) - 401 Unauthorized")
        void should_return401_when_accountIsInactive() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", INACTIVE_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("メールアドレスまたはパスワードが違います"));
        }

        /**
         * TC-05: username(メール)が空文字 → formLogin は 401 を返す
         *        注: formLogin エンドポイントは @Valid が効かないため Spring Security の認証失敗として扱われる
         */
        @Test
        @DisplayName("TC-05: メール空欄 - 401 Unauthorized (formLogin認証失敗)")
        void should_return401_when_usernameIsBlank() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", "")
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isUnauthorized());
        }

        /**
         * TC-06: password が空文字 → formLogin は 401 を返す
         *        注: formLogin エンドポイントは @Valid が効かないため Spring Security の認証失敗として扱われる
         */
        @Test
        @DisplayName("TC-06: パスワード空欄 - 401 Unauthorized (formLogin認証失敗)")
        void should_return401_when_passwordIsBlank() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", ""))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================================================
    // POST /api/auth/logout
    // ============================================================
    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        /**
         * TC-07: 認証済みセッションでログアウト → 200 OK + セッション無効化
         *        先にログインしてセッションを取得し、同セッションでログアウトする
         */
        @Test
        @DisplayName("TC-07: 認証済みでログアウト - 200 OK とセッション削除")
        void should_returnOk_and_invalidateSession_when_authenticated() throws Exception {
            // Arrange: ログインしてセッションを取得
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isOk())
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

            // Act & Assert: 同セッションでログアウト
            mockMvc.perform(post("/api/auth/logout")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("ログアウトしました"));
        }

        /**
         * TC-08: 未認証（セッションなし）でログアウト → 200 OK
         *        Spring Security の logout フィルターは認証状態に関わらずリクエストを処理し、
         *        logoutSuccessHandler が 200 を返す設計のため、未認証でも 200 となる。
         *        このテストはその仕様を明示的に記録する。
         */
        @Test
        @DisplayName("TC-08: 未認証でログアウト - 200 OK（logoutSuccessHandler が応答）")
        void should_return200_when_notAuthenticatedLogout() throws Exception {
            mockMvc.perform(post("/api/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("ログアウトしました"));
        }
    }

    // ============================================================
    // GET /api/auth/me
    // ============================================================
    @Nested
    @DisplayName("GET /api/auth/me")
    class Me {

        /**
         * TC-09: 認証済みで /me → 200 OK + StaffResponse の主要フィールドを検証
         */
        @Test
        @DisplayName("TC-09: 認証済み - 200 OK と StaffResponse を返す")
        void should_returnOk_and_staffResponse_when_authenticated() throws Exception {
            // Arrange: ログインしてセッションを取得
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isOk())
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

            // Act & Assert
            mockMvc.perform(get("/api/auth/me")
                            .session(session))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.email").value(ACTIVE_EMAIL))
                    .andExpect(jsonPath("$.name").value("有効スタッフ"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.isActive").value(true))
                    .andExpect(jsonPath("$.forcePasswordChange").value(false));
        }

        /**
         * TC-10: 未認証で /me → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-10: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-10b: ログアウト後に /me → 401 Unauthorized（セッション無効化の確認）
         */
        @Test
        @DisplayName("TC-10b: ログアウト後のセッション再利用 - 401 Unauthorized")
        void should_return401_when_sessionInvalidatedAfterLogout() throws Exception {
            // Arrange: ログイン → ログアウト → 同セッションで /me
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

            mockMvc.perform(post("/api/auth/logout").session(session))
                    .andExpect(status().isOk());

            // Act & Assert: 無効化されたセッションでアクセス
            mockMvc.perform(get("/api/auth/me").session(session))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================================================
    // POST /api/auth/password/change
    // （AuthController は @PostMapping を使用している）
    // ============================================================
    @Nested
    @DisplayName("POST /api/auth/password/change")
    class ChangePassword {

        /**
         * TC-11: 認証済みで正常なパスワード変更 → 200 OK
         *        AuthService.changePassword が呼ばれ、StaffRepository.save が実行されること
         */
        @Test
        @DisplayName("TC-11: 正常なパスワード変更 - 200 OK")
        void should_returnOk_when_validPasswordChangeRequest() throws Exception {
            // Arrange: ログイン
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isOk())
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

            // Act & Assert
            mockMvc.perform(post("/api/auth/password/change")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"newSecurePass1\"}"))
                    .andExpect(status().isOk());

            // パスワード更新のために save が呼ばれたことを確認
            verify(staffRepository).save(any(Staff.class));
        }

        /**
         * TC-11b: 新パスワードが 8 文字未満 → 400 Bad Request（@Size バリデーション）
         */
        @Test
        @DisplayName("TC-11b: 新パスワードが8文字未満 - 400 Bad Request")
        void should_return400_when_newPasswordTooShort() throws Exception {
            // Arrange: ログイン
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isOk())
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

            // Act & Assert
            mockMvc.perform(post("/api/auth/password/change")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"short\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors.newPassword").exists());
        }

        /**
         * TC-11c: 新パスワードが null → 400 Bad Request（@NotBlank バリデーション）
         */
        @Test
        @DisplayName("TC-11c: 新パスワードがnull - 400 Bad Request")
        void should_return400_when_newPasswordIsNull() throws Exception {
            // Arrange: ログイン
            MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("username", ACTIVE_EMAIL)
                            .param("password", RAW_PASSWORD))
                    .andExpect(status().isOk())
                    .andReturn();

            MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

            // Act & Assert
            mockMvc.perform(post("/api/auth/password/change")
                            .session(session)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.newPassword").exists());
        }

        /**
         * TC-12: 未認証でパスワード変更 → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-12: 未認証でパスワード変更 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(post("/api/auth/password/change")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"newSecurePass1\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }
    }

    // ============================================================
    // その他: ADMIN ロール制限の確認
    // ============================================================
    @Nested
    @DisplayName("認可テスト（ROLE_ADMIN 要件）")
    class Authorization {

        /**
         * TC-13: USER ロールで /api/staffs/** にアクセス → 403 Forbidden
         *        @WithMockUser はロール付きの Authentication をセットするため、
         *        実際のログインなしに認可テストを簡潔に記述できる
         */
        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("TC-13: USERロールで /api/staffs にアクセス - 403 Forbidden")
        void should_return403_when_userRoleAccessesStaffsEndpoint() throws Exception {
            mockMvc.perform(get("/api/staffs"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }

        /**
         * TC-14: ADMIN ロールで /api/staffs/** にアクセス → 403 以外が返る（認可通過）
         *        実際のエンドポイント実装への委譲は StaffController のテストで検証するため、
         *        ここでは 403 が返らないことのみ確認する
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-14: ADMINロールで /api/staffs にアクセス - 403 以外（認可通過）")
        void should_notReturn403_when_adminRoleAccessesStaffsEndpoint() throws Exception {
            mockMvc.perform(get("/api/staffs"))
                    .andExpect(result ->
                            org.junit.jupiter.api.Assertions.assertNotEquals(
                                    403, result.getResponse().getStatus(),
                                    "ADMIN ロールは /api/staffs へのアクセスが許可されているべきです"
                            )
                    );
        }
    }
}
