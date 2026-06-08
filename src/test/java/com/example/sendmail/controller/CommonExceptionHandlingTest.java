package com.example.sendmail.controller;

import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.service.UserService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #9 共通（GlobalExceptionHandler / 認可 / 静的ファイル）— spec No.94〜100
 *
 * テスト仕様書「バックエンド_テスト仕様書.xlsx」の #9 共通カテゴリに 1:1 で対応する。
 * GlobalExceptionHandler・SecurityConfig はコントローラー横断の共通処理であるため、
 * UserController（/api/users 系）のエンドポイントを題材に UserService を
 * @MockitoBean でモックすることで、各種例外・認可シナリオを再現して検証する。
 *
 * 設計方針:
 *   - @SpringBootTest + @AutoConfigureMockMvc で実際の SecurityFilterChain と
 *     GlobalExceptionHandler（@RestControllerAdvice）を通す
 *   - UserService を @MockitoBean でモックし、各 @ExceptionHandler に対応する例外を
 *     スローさせることで、共通のエラーレスポンス形式（message・errors）を検証する
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("#9 共通（GlobalExceptionHandler・認可・静的ファイル）")
class CommonExceptionHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Nested
    @DisplayName("GlobalExceptionHandler — 例外ハンドリング")
    class ExceptionHandling {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.94: GlobalExceptionHandler: バリデーションエラー（MethodArgumentNotValidException）発生時に400とエラー内容のJSONが返る")
        void no94_validationError_returns400WithErrorDetails() throws Exception {
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"name": ""}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("入力値が不正です"))
                    .andExpect(jsonPath("$.errors").exists())
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.95: GlobalExceptionHandler: ResourceNotFoundException発生時に404とエラーメッセージのJSONが返る")
        void no95_resourceNotFoundException_returns404WithMessage() throws Exception {
            when(userService.getUser(999L))
                    .thenThrow(new ResourceNotFoundException("利用者が見つかりません: 999"));

            mockMvc.perform(get("/api/users/{id}", 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("利用者が見つかりません: 999"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("No.96: GlobalExceptionHandler: DuplicateResourceException発生時に409とエラーメッセージのJSONが返る")
        void no96_duplicateResourceException_returns409WithMessage() throws Exception {
            when(userService.addOfficeToUser(anyLong(), any()))
                    .thenThrow(new DuplicateResourceException("既に紐付けが存在します"));

            mockMvc.perform(post("/api/users/{userId}/offices", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"officeId": 10}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("既に紐付けが存在します"));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.97: GlobalExceptionHandler: 想定外の例外発生時に500と汎用エラーメッセージのJSONが返る")
        void no97_unexpectedException_returns500WithGenericMessage() throws Exception {
            when(userService.getUser(1L)).thenThrow(new RuntimeException("想定外の内部エラー"));

            mockMvc.perform(get("/api/users/{id}", 1L))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("サーバーエラーが発生しました"));
        }
    }

    @Nested
    @DisplayName("認可 — 共通の認証・認可制御")
    class CommonAuthorization {

        @Test
        @DisplayName("No.98: 認可: 未認証で保護されたエンドポイントにアクセスすると401が返る")
        void no98_unauthenticated_protectedEndpoint_returns401() throws Exception {
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("No.99: 認可: STAFF権限でADMIN専用エンドポイント（/api/staffs/**, DELETE系）にアクセスすると403が返る")
        void no99_staffRole_adminOnlyEndpoints_returns403() throws Exception {
            // /api/staffs/** は SecurityConfig の URL レベル制御（hasRole("ADMIN")）で保護される
            mockMvc.perform(get("/api/staffs"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));

            // DELETE 系（UserController#deactivateUser）は @PreAuthorize("hasRole('ADMIN')") で保護される
            mockMvc.perform(delete("/api/users/{id}", 1L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("権限がありません"));
        }
    }

    @Nested
    @DisplayName("CORS/静的ファイル — API以外のパスの扱い")
    class StaticResources {

        @Test
        @DisplayName("No.100: CORS/静的ファイル: API以外のパス（React静的ファイル）はSecurityConfigでpermitAllとなっている")
        void no100_nonApiPaths_arePermitAll() throws Exception {
            // SecurityConfig#filterChain の anyRequest().permitAll() により、
            // /api/** に該当しないパスは未認証でもアクセスできる
            // （実体が存在しない場合でも、認証フィルターで弾かれず 401/403 にはならないことを確認する）
            mockMvc.perform(get("/index.html"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.junit.jupiter.api.Assertions.assertTrue(
                                status != 401 && status != 403,
                                "API以外のパスは認証フィルターで弾かれるべきではない（実際のステータス: " + status + "）"
                        );
                    });
        }
    }
}
