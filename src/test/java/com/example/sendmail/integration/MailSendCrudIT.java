package com.example.sendmail.integration;

import com.example.sendmail.domain.entity.MailSend;
import com.example.sendmail.domain.entity.Office;
import com.example.sendmail.domain.entity.User;
import com.example.sendmail.domain.enums.SendType;
import com.example.sendmail.dto.request.CreateMailSendRequest;
import com.example.sendmail.dto.response.MailSendResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.repository.MailSendRepository;
import com.example.sendmail.repository.OfficeRepository;
import com.example.sendmail.repository.UserRepository;
import com.example.sendmail.service.MailSendService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * mail-sends の CRUD 一連の流れを実MySQL上で検証する結合テスト。
 *
 * send_type / status は MySQL の ENUM 型として定義されており（schema-mysql.sql）、
 * H2 では MODE=MySQL でも厳密に同じ挙動が保証されないため、実DBで確認する。
 */
@DisplayName("送付レコード（MailSend）のCRUDが実MySQL上で正しく動く")
class MailSendCrudIT extends AbstractIntegrationTest {

    @Autowired
    private MailSendService mailSendService;

    @Autowired
    private MailSendRepository mailSendRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfficeRepository officeRepository;

    private Long userId;
    private Long officeId;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setName("山田太郎");
        user.setNameKana("ヤマダタロウ");
        userId = userRepository.save(user).getId();

        Office office = new Office();
        office.setName("中央事業所");
        officeId = officeRepository.save(office).getId();
    }

    @Test
    @DisplayName("作成→一覧→更新→削除の一連の流れが成功する")
    void createListUpdateDelete_succeeds() {
        CreateMailSendRequest createReq = new CreateMailSendRequest(userId, officeId, SendType.PLAN, LocalDate.of(2026, 6, 15));

        MailSendResponse created = mailSendService.createMailSend(createReq, "admin@example.com");

        // 送付月は月初日に正規化されて MySQL の DATE 型に保存される
        assertThat(created.sendMonth()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(created.status().name()).isEqualTo("PENDING");

        MailSend persisted = mailSendRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getSendType()).isEqualTo(SendType.PLAN);

        var list = mailSendService.listMailSends(null, null, null, null, null, null);
        assertThat(list).extracting(MailSendResponse::id).contains(created.id());

        CreateMailSendRequest updateReq = new CreateMailSendRequest(userId, officeId, SendType.MONITORING, LocalDate.of(2026, 7, 1));
        MailSendResponse updated = mailSendService.updateMailSend(created.id(), updateReq);
        assertThat(updated.sendType()).isEqualTo(SendType.MONITORING);

        mailSendService.deleteMailSend(created.id());
        assertThat(mailSendRepository.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("同じ利用者×事業所×送付種別×送付月の組み合わせは重複エラーになる")
    void duplicateCombination_throwsDuplicateResourceException() {
        CreateMailSendRequest req = new CreateMailSendRequest(userId, officeId, SendType.PLAN, LocalDate.of(2026, 6, 1));

        mailSendService.createMailSend(req, "admin@example.com");

        assertThatThrownBy(() -> mailSendService.createMailSend(req, "admin@example.com"))
                .isInstanceOf(DuplicateResourceException.class);
    }
}
