package com.example.sendmail.controller;

import com.example.sendmail.domain.entity.Role;
import com.example.sendmail.repository.RoleRepository;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RoleController の統合テスト。
 *
 * 設計方針:
 *   - @SpringBootTest で実際の SecurityFilterChain を通す
 *   - RoleRepository を @MockitoBean でモックし、DB 接続なしにインメモリで完結
 *   - @WithMockUser で認証状態を表現する
 *   - /api/roles/** は SecurityConfig で authenticated() のみ（ロール制限なし、ADMIN/STAFFどちらも可）
 *   - テスト ID は TC-ROLE-01 から連番
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("RoleController 統合テスト")
class RoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleRepository roleRepository;

    private static final String BASE_URL = "/api/roles";

    private Role adminRole;
    private Role staffRole;

    @BeforeEach
    void setUp() {
        adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setName("ADMIN");

        staffRole = new Role();
        staffRole.setId(2L);
        staffRole.setName("STAFF");
    }

    // ============================================================
    // GET /api/roles
    // ============================================================
    @Nested
    @DisplayName("GET /api/roles — ロール一覧取得")
    class ListRoles {

        /**
         * TC-ROLE-01: 認証済みユーザー（ADMIN）がロール一覧を取得 → 200 OK
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-ROLE-01: 認証済みADMIN - 200 OK + ロール一覧を返す")
        void should_return200_when_adminListsRoles() throws Exception {
            when(roleRepository.findAll()).thenReturn(List.of(adminRole, staffRole));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("ADMIN"))
                    .andExpect(jsonPath("$[1].id").value(2))
                    .andExpect(jsonPath("$[1].name").value("STAFF"));
        }

        /**
         * TC-ROLE-02: 認証済みユーザー（STAFF）がロール一覧を取得 → 200 OK
         * /api/roles はロール制限なし（authenticated() のみ）
         */
        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-ROLE-02: 認証済みSTAFF - 200 OK（ロール制限なし）")
        void should_return200_when_staffListsRoles() throws Exception {
            when(roleRepository.findAll()).thenReturn(List.of(adminRole, staffRole));

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        /**
         * TC-ROLE-03: 未認証でアクセス → 401 Unauthorized
         */
        @Test
        @DisplayName("TC-ROLE-03: 未認証 - 401 Unauthorized")
        void should_return401_when_notAuthenticated() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        /**
         * TC-ROLE-04: ロールが0件 → 200 OK + 空配列
         */
        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-ROLE-04: ロールが0件 - 200 OK + 空配列")
        void should_return200_and_emptyList_when_noRoleExists() throws Exception {
            when(roleRepository.findAll()).thenReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }
}
