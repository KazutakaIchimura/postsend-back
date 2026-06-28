package com.example.sendmail.service;

import com.example.sendmail.domain.entity.MailSend;
import com.example.sendmail.domain.entity.Office;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.domain.entity.User;
import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.domain.enums.SendType;
import com.example.sendmail.dto.request.CreateMailSendRequest;
import com.example.sendmail.dto.response.MailSendResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.InvalidStatusException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.MailSendRepository;
import com.example.sendmail.repository.OfficeRepository;
import com.example.sendmail.repository.StaffRepository;
import com.example.sendmail.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MailSendService")
class MailSendServiceTest {

    @Mock
    private MailSendRepository mailSendRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OfficeRepository officeRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private AccessLogService accessLogService;

    @InjectMocks
    private MailSendService mailSendService;

    private User user;
    private Office office;
    private Staff staff;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setName("山田太郎");
        user.setIsActive(true);

        office = new Office();
        office.setId(10L);
        office.setName("中央事業所");
        office.setIsActive(true);

        staff = new Staff();
        staff.setId(1L);
        staff.setEmail("staff@example.com");
    }

    // ヘルパー: MailSend エンティティをビルド
    private MailSend buildMailSend(Long id, SendStatus status, LocalDate sendMonth) {
        MailSend ms = new MailSend();
        ms.setUser(user);
        ms.setOffice(office);
        ms.setSendType(SendType.PLAN);
        ms.setSendMonth(sendMonth);
        ms.setStatus(status);
        // idはリフレクションで設定（@GeneratedValueのため）
        try {
            var f = MailSend.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(ms, id);
        } catch (Exception ignored) {}
        return ms;
    }

    // ============================================================
    // listMailSends
    // ============================================================

    @Nested
    @DisplayName("listMailSends — 一覧取得")
    class ListMailSends {

        @Test
        @DisplayName("不正なstatusを指定するとIllegalArgumentExceptionをスロー")
        void listMailSends_invalidStatus_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> mailSendService.listMailSends(
                    "INVALID_STATUS", null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ステータスの値が不正です: INVALID_STATUS");
        }

        @Test
        @DisplayName("statusがnullの場合はnullとしてリポジトリに渡る")
        void listMailSends_nullStatus_passesNullToRepo() {
            LocalDate today = LocalDate.now().withDayOfMonth(1);
            MailSend ms = buildMailSend(1L, SendStatus.PENDING, today);
            when(mailSendRepository.findFiltered(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of(ms));

            List<MailSendResponse> result = mailSendService.listMailSends(
                    null, null, null, null, null, null);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("空文字のstatusはnullとして扱われる")
        void listMailSends_emptyStatus_treatedAsNull() {
            when(mailSendRepository.findFiltered(isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(List.of());

            mailSendService.listMailSends("", null, null, null, null, null);

            verify(mailSendRepository).findFiltered(isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
        }
    }

    // ============================================================
    // createMailSend
    // ============================================================

    @Nested
    @DisplayName("createMailSend — 送付レコード登録")
    class CreateMailSend {

        @Test
        @DisplayName("send_monthが月途中の日付でも月初日に正規化されて重複チェックと保存が行われる")
        void createMailSend_sendMonthNormalizedToFirstDay() {
            CreateMailSendRequest req = new CreateMailSendRequest(1L, 10L, SendType.PLAN, LocalDate.of(2026, 6, 15)); // 月の途中

            LocalDate expectedFirstDay = LocalDate.of(2026, 6, 1);

            when(mailSendRepository.existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
                    1L, 10L, SendType.PLAN, expectedFirstDay)).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(officeRepository.findById(10L)).thenReturn(Optional.of(office));
            when(staffRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staff));

            MailSend saved = buildMailSend(100L, SendStatus.PENDING, expectedFirstDay);
            when(mailSendRepository.save(any())).thenReturn(saved);

            MailSendResponse result = mailSendService.createMailSend(req, "staff@example.com");

            assertThat(result.sendMonth()).isEqualTo(expectedFirstDay);
            // 重複チェックは月初日で行われる
            verify(mailSendRepository).existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
                    1L, 10L, SendType.PLAN, expectedFirstDay);
        }

        @Test
        @DisplayName("同一組み合わせが存在する場合DuplicateResourceExceptionをスロー")
        void createMailSend_duplicate_throwsDuplicateResourceException() {
            CreateMailSendRequest req = new CreateMailSendRequest(1L, 10L, SendType.PLAN, LocalDate.of(2026, 6, 1));

            when(mailSendRepository.existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
                    1L, 10L, SendType.PLAN, LocalDate.of(2026, 6, 1))).thenReturn(true);

            assertThatThrownBy(() -> mailSendService.createMailSend(req, "staff@example.com"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("同じ送付レコードが既に存在します");

            verify(mailSendRepository, never()).save(any());
        }

        @Test
        @DisplayName("存在しないuserIdを指定するとResourceNotFoundExceptionをスロー")
        void createMailSend_nonExistingUser_throwsResourceNotFoundException() {
            CreateMailSendRequest req = new CreateMailSendRequest(999L, 10L, SendType.PLAN, LocalDate.of(2026, 6, 1));

            when(mailSendRepository.existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
                    999L, 10L, SendType.PLAN, LocalDate.of(2026, 6, 1))).thenReturn(false);
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mailSendService.createMailSend(req, "staff@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("利用者が見つかりません: 999");

            verify(mailSendRepository, never()).save(any());
        }

        @Test
        @DisplayName("存在しないofficeIdを指定するとResourceNotFoundExceptionをスロー")
        void createMailSend_nonExistingOffice_throwsResourceNotFoundException() {
            CreateMailSendRequest req = new CreateMailSendRequest(1L, 999L, SendType.PLAN, LocalDate.of(2026, 6, 1));

            when(mailSendRepository.existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
                    1L, 999L, SendType.PLAN, LocalDate.of(2026, 6, 1))).thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(officeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mailSendService.createMailSend(req, "staff@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("事業所が見つかりません: 999");
        }

        @Test
        @DisplayName("登録成功時にaccessLogService.logが呼ばれる")
        void createMailSend_success_logsCreate() {
            CreateMailSendRequest req = new CreateMailSendRequest(1L, 10L, SendType.MONITORING, LocalDate.of(2026, 6, 1));

            when(mailSendRepository.existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(any(), any(), any(), any()))
                    .thenReturn(false);
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));
            when(officeRepository.findById(10L)).thenReturn(Optional.of(office));
            when(staffRepository.findByEmailIgnoreCase("staff@example.com")).thenReturn(Optional.of(staff));

            MailSend saved = buildMailSend(50L, SendStatus.PENDING, LocalDate.of(2026, 6, 1));
            when(mailSendRepository.save(any())).thenReturn(saved);

            mailSendService.createMailSend(req, "staff@example.com");

            verify(accessLogService).log("CREATE", "MAIL_SEND", 50L);
        }
    }

    // ============================================================
    // updateMailSend
    // ============================================================

    @Nested
    @DisplayName("updateMailSend — 送付レコード更新")
    class UpdateMailSend {

        @Test
        @DisplayName("status=PENDING以外のレコードを更新しようとするとInvalidStatusExceptionをスロー")
        void updateMailSend_nonPending_throwsInvalidStatusException() {
            MailSend sentMs = buildMailSend(1L, SendStatus.SENT, LocalDate.of(2026, 6, 1));
            when(mailSendRepository.findById(1L)).thenReturn(Optional.of(sentMs));

            CreateMailSendRequest req = new CreateMailSendRequest(1L, 10L, SendType.PLAN, LocalDate.of(2026, 7, 1));

            assertThatThrownBy(() -> mailSendService.updateMailSend(1L, req))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("PENDING以外のレコードは更新できません");

            verify(mailSendRepository, never()).save(any());
        }

        @Test
        @DisplayName("status=PENDINGのレコードは正常に更新される")
        void updateMailSend_pending_updatesSuccessfully() {
            LocalDate oldMonth = LocalDate.of(2026, 6, 1);
            LocalDate newMonth = LocalDate.of(2026, 7, 1);
            MailSend pendingMs = buildMailSend(1L, SendStatus.PENDING, oldMonth);
            when(mailSendRepository.findById(1L)).thenReturn(Optional.of(pendingMs));
            when(mailSendRepository.save(pendingMs)).thenReturn(pendingMs);

            CreateMailSendRequest req = new CreateMailSendRequest(1L, 10L, SendType.MONITORING, newMonth);

            mailSendService.updateMailSend(1L, req);

            assertThat(pendingMs.getSendMonth()).isEqualTo(newMonth);
            assertThat(pendingMs.getSendType()).isEqualTo(SendType.MONITORING);
            verify(accessLogService).log("UPDATE", "MAIL_SEND", 1L);
        }

        @Test
        @DisplayName("存在しないIDを指定するとResourceNotFoundExceptionをスロー")
        void updateMailSend_nonExistingId_throwsResourceNotFoundException() {
            when(mailSendRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mailSendService.updateMailSend(999L, new CreateMailSendRequest(null, null, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("送付レコードが見つかりません: 999");
        }
    }

    // ============================================================
    // deleteMailSend
    // ============================================================

    @Nested
    @DisplayName("deleteMailSend — 送付レコード削除")
    class DeleteMailSend {

        @Test
        @DisplayName("status=PENDING以外のレコードを削除しようとするとInvalidStatusExceptionをスロー")
        void deleteMailSend_nonPending_throwsInvalidStatusException() {
            MailSend sentMs = buildMailSend(1L, SendStatus.SENT, LocalDate.of(2026, 6, 1));
            when(mailSendRepository.findById(1L)).thenReturn(Optional.of(sentMs));

            assertThatThrownBy(() -> mailSendService.deleteMailSend(1L))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("PENDING以外のレコードは削除できません");

            verify(mailSendRepository, never()).delete(any());
        }

        @Test
        @DisplayName("status=PENDINGのレコードは正常に削除される")
        void deleteMailSend_pending_deletesSuccessfully() {
            MailSend pendingMs = buildMailSend(1L, SendStatus.PENDING, LocalDate.of(2026, 6, 1));
            when(mailSendRepository.findById(1L)).thenReturn(Optional.of(pendingMs));

            mailSendService.deleteMailSend(1L);

            verify(mailSendRepository).delete(pendingMs);
            verify(accessLogService).log("DELETE", "MAIL_SEND", 1L);
        }

        @Test
        @DisplayName("存在しないIDを指定するとResourceNotFoundExceptionをスロー")
        void deleteMailSend_nonExistingId_throwsResourceNotFoundException() {
            when(mailSendRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mailSendService.deleteMailSend(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("送付レコードが見つかりません: 999");
        }
    }
}
