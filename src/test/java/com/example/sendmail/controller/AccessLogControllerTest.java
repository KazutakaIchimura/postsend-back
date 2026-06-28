package com.example.sendmail.controller;

import com.example.sendmail.domain.entity.AccessLog;
import com.example.sendmail.service.AccessLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AccessLogController 統合テスト
 *
 * 設計方針:
 *   - @SpringBootTest + @AutoConfigureMockMvc で実際の SecurityFilterChain を通す
 *   - AccessLogService を @MockitoBean でモックし、コントローラー層の入出力・認可のみ検証する
 *   - /api/access-logs/** は SecurityConfig で hasRole("ADMIN") に制限（STAFF は 403）
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AccessLogController 統合テスト")
class AccessLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccessLogService accessLogService;

    private static final String BASE_URL = "/api/access-logs";

    @Nested
    @DisplayName("GET /api/access-logs — アクセスログ一覧取得")
    class ListAccessLogs {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-ALOG-01: ADMIN権限でアクセスログ一覧が200で返る")
        void tc_alog_01_adminListsAccessLogs_returns200() throws Exception {
            when(accessLogService.findAll(any())).thenReturn(Page.empty());

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @WithMockUser(roles = "STAFF")
        @DisplayName("TC-ALOG-02: STAFF権限でアクセスすると403が返る（ADMIN専用エンドポイント）")
        void tc_alog_02_staffListsAccessLogs_returns403() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("TC-ALOG-03: 未認証でアクセスすると401が返る")
        void tc_alog_03_unauthenticated_returns401() throws Exception {
            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("認証が必要です"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-ALOG-04: page・sizeクエリパラメータがサービスに正しく渡る")
        void tc_alog_04_pageAndSizePassedToService() throws Exception {
            when(accessLogService.findAll(any())).thenReturn(Page.empty());

            mockMvc.perform(get(BASE_URL).param("page", "2").param("size", "20"))
                    .andExpect(status().isOk());

            verify(accessLogService).findAll(PageRequest.of(2, 20));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-ALOG-05: sizeが200を超える場合は200に切り詰められてサービスに渡る")
        void tc_alog_05_sizeExceeds200_cappedAt200() throws Exception {
            when(accessLogService.findAll(any())).thenReturn(Page.empty());

            mockMvc.perform(get(BASE_URL).param("size", "500"))
                    .andExpect(status().isOk());

            verify(accessLogService).findAll(PageRequest.of(0, 200));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-ALOG-06: ログが存在する場合にaction・resourceType・staffEmailが返る")
        void tc_alog_06_logsExist_fieldsReturnedCorrectly() throws Exception {
            AccessLog log = new AccessLog();
            log.setAction("CREATE");
            log.setResourceType("USER");
            log.setResourceId(1L);
            log.setStaffEmail("admin@example.com");

            Page<AccessLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 50), 1);
            when(accessLogService.findAll(any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].action").value("CREATE"))
                    .andExpect(jsonPath("$.content[0].resourceType").value("USER"))
                    .andExpect(jsonPath("$.content[0].staffEmail").value("admin@example.com"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("TC-ALOG-07: ページネーション情報（totalElements・totalPages・number）が返る")
        void tc_alog_07_paginationMetaReturned() throws Exception {
            AccessLog log = new AccessLog();
            log.setAction("VIEW");
            log.setResourceType("USER");
            log.setResourceId(5L);

            Page<AccessLog> page = new PageImpl<>(List.of(log), PageRequest.of(0, 50), 100L);
            when(accessLogService.findAll(any())).thenReturn(page);

            mockMvc.perform(get(BASE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(100))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.number").value(0));
        }
    }
}
