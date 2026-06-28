package com.example.sendmail.service;

import com.example.sendmail.domain.entity.MailSend;
import com.example.sendmail.domain.entity.MailSendBatch;
import com.example.sendmail.domain.entity.Office;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.domain.entity.User;
import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.domain.enums.SendType;
import com.example.sendmail.dto.request.CreateMailSendBatchRequest;
import com.example.sendmail.dto.response.MailSendBatchResponse;
import com.example.sendmail.exception.InvalidStatusException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.MailSendBatchRepository;
import com.example.sendmail.repository.MailSendRepository;
import com.example.sendmail.repository.StaffRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MailSendBatchService")
class MailSendBatchServiceTest {

    @Mock
    private MailSendBatchRepository mailSendBatchRepository;
    @Mock
    private MailSendRepository mailSendRepository;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private AccessLogService accessLogService;

    @InjectMocks
    private MailSendBatchService mailSendBatchService;

    private Staff sentByStaff;
    private MailSend pendingMs1;
    private MailSend pendingMs2;

    @BeforeEach
    void setUp() {
        sentByStaff = new Staff();
        sentByStaff.setId(1L);
        sentByStaff.setEmail("staff@example.com");

        User user = new User();
        user.setId(1L);
        user.setName("山田太郎");

        Office office = new Office();
        office.setId(10L);
        office.setName("中央事業所");

        pendingMs1 = new MailSend();
        pendingMs1.setUser(user);
        pendingMs1.setOffice(office);
        pendingMs1.setSendType(SendType.PLAN);
        pendingMs1.setSendMonth(LocalDate.of(2026, 6, 1));
        pendingMs1.setStatus(SendStatus.PENDING);
        try {
            var f = MailSend.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(pendingMs1, 1L);
        } catch (Exception ignored) {}

        pendingMs2 = new MailSend();
        pendingMs2.setUser(user);
        pendingMs2.setOffice(office);
        pendingMs2.setSendType(SendType.MONITORING);
        pendingMs2.setSendMonth(LocalDate.of(2026, 6, 1));
        pendingMs2.setStatus(SendStatus.PENDING);
        try {
            var f = MailSend.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(pendingMs2, 2L);
        } catch (Exception ignored) {}
    }

    // ============================================================
    // createBatch
    // ============================================================

    @Nested
    @DisplayName("createBatch — バッチ送付済み処理")
    class CreateBatch {

        @Test
        @DisplayName("正常なリクエストで対象レコードのstatusがSENTに更新され、バッチ情報が返る")
        void createBatch_success_updatesStatusAndReturnsBatchResponse() {
            CreateMailSendBatchRequest req = new CreateMailSendBatchRequest();
            req.setMailSendIds(List.of(1L, 2L));
            req.setNotes("6月分送付");

            when(staffRepository.findByEmailIgnoreCase("staff@example.com"))
                    .thenReturn(Optional.of(sentByStaff));
            when(mailSendRepository.findAllById(Set.of(1L, 2L)))
                    .thenReturn(List.of(pendingMs1, pendingMs2));

            MailSendBatch savedBatch = new MailSendBatch();
            try {
                var f = MailSendBatch.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(savedBatch, 100L);
            } catch (Exception ignored) {}
            savedBatch.setSentAt(LocalDateTime.of(2026, 6, 10, 10, 0));
            savedBatch.setNotes("6月分送付");

            when(mailSendBatchRepository.save(any())).thenReturn(savedBatch);
            when(mailSendRepository.saveAll(any())).thenReturn(List.of(pendingMs1, pendingMs2));

            MailSendBatchResponse result = mailSendBatchService.createBatch(req, "staff@example.com");

            assertThat(result.getBatchId()).isEqualTo(100L);
            assertThat(result.getUpdatedCount()).isEqualTo(2);
            assertThat(result.getNotes()).isEqualTo("6月分送付");

            // status が SENT に更新されている
            assertThat(pendingMs1.getStatus()).isEqualTo(SendStatus.SENT);
            assertThat(pendingMs2.getStatus()).isEqualTo(SendStatus.SENT);

            verify(accessLogService).log("CREATE", "BATCH", 100L);
        }

        @Test
        @DisplayName("存在しないmailSendIdを含む場合ResourceNotFoundExceptionをスロー")
        void createBatch_nonExistingMailSendId_throwsResourceNotFoundException() {
            CreateMailSendBatchRequest req = new CreateMailSendBatchRequest();
            req.setMailSendIds(List.of(1L, 999L));

            when(staffRepository.findByEmailIgnoreCase("staff@example.com"))
                    .thenReturn(Optional.of(sentByStaff));
            // 2件要求したが1件しか返らない → 件数不一致
            when(mailSendRepository.findAllById(any()))
                    .thenReturn(List.of(pendingMs1));

            assertThatThrownBy(() -> mailSendBatchService.createBatch(req, "staff@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("存在しない送付レコードが含まれています");

            verify(mailSendBatchRepository, never()).save(any());
        }

        @Test
        @DisplayName("PENDING以外のレコードを含む場合InvalidStatusExceptionをスロー")
        void createBatch_containsNonPendingRecord_throwsInvalidStatusException() {
            MailSend sentMs = new MailSend();
            sentMs.setStatus(SendStatus.SENT);
            try {
                var f = MailSend.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(sentMs, 3L);
            } catch (Exception ignored) {}

            CreateMailSendBatchRequest req = new CreateMailSendBatchRequest();
            req.setMailSendIds(List.of(1L, 3L));

            when(staffRepository.findByEmailIgnoreCase("staff@example.com"))
                    .thenReturn(Optional.of(sentByStaff));
            when(mailSendRepository.findAllById(any()))
                    .thenReturn(List.of(pendingMs1, sentMs));

            assertThatThrownBy(() -> mailSendBatchService.createBatch(req, "staff@example.com"))
                    .isInstanceOf(InvalidStatusException.class)
                    .hasMessageContaining("PENDING以外のレコードが含まれています");

            verify(mailSendBatchRepository, never()).save(any());
        }

        @Test
        @DisplayName("存在しないスタッフのメールアドレスを指定するとResourceNotFoundExceptionをスロー")
        void createBatch_unknownStaffEmail_throwsResourceNotFoundException() {
            CreateMailSendBatchRequest req = new CreateMailSendBatchRequest();
            req.setMailSendIds(List.of(1L));

            when(staffRepository.findByEmailIgnoreCase("unknown@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> mailSendBatchService.createBatch(req, "unknown@example.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("スタッフが見つかりません");
        }
    }

    // ============================================================
    // getBatch
    // ============================================================

    @Nested
    @DisplayName("getBatch — バッチ詳細取得")
    class GetBatch {

        @Test
        @DisplayName("存在するbatchIdでバッチ詳細が返る")
        void getBatch_existingId_returnsBatchResponse() {
            MailSendBatch batch = new MailSendBatch();
            try {
                var f = MailSendBatch.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(batch, 100L);
            } catch (Exception ignored) {}
            batch.setSentAt(LocalDateTime.of(2026, 6, 10, 10, 0));
            batch.setNotes("6月分");

            when(mailSendBatchRepository.findById(100L)).thenReturn(Optional.of(batch));
            when(mailSendRepository.countByBatchId(100L)).thenReturn(3L);

            MailSendBatchResponse result = mailSendBatchService.getBatch(100L);

            assertThat(result.getBatchId()).isEqualTo(100L);
            assertThat(result.getUpdatedCount()).isEqualTo(3);
            assertThat(result.getNotes()).isEqualTo("6月分");
        }

        @Test
        @DisplayName("存在しないbatchIdを指定するとResourceNotFoundExceptionをスロー")
        void getBatch_nonExistingId_throwsResourceNotFoundException() {
            when(mailSendBatchRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> mailSendBatchService.getBatch(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("バッチが見つかりません: 999");
        }
    }
}
