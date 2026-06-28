package com.example.sendmail.service;

import com.example.sendmail.domain.entity.MailSend;
import com.example.sendmail.domain.entity.Office;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.domain.entity.User;
import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.dto.request.CreateMailSendRequest;
import com.example.sendmail.dto.response.MailSendByOfficeResponse;
import com.example.sendmail.dto.response.MailSendResponse;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.InvalidStatusException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.MailSendRepository;
import com.example.sendmail.repository.OfficeRepository;
import com.example.sendmail.repository.StaffRepository;
import com.example.sendmail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MailSendService {

    private final MailSendRepository mailSendRepository;
    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;
    private final StaffRepository staffRepository;
    private final AccessLogService accessLogService;

    @Transactional(readOnly = true)
    public List<MailSendResponse> listMailSends(
            String status, String sendMonth, Long userId, Long officeId,
            String dateFrom, String dateTo) {
        return buildFilteredResponses(
                parseStatus(status), parseYearMonth(sendMonth), userId, officeId,
                parseYearMonth(dateFrom), parseYearMonth(dateTo));
    }

    @Transactional(readOnly = true)
    public List<MailSendByOfficeResponse> listByOffice(String status) {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        SendStatus statusEnum = parseStatus(status);

        return mailSendRepository.findFiltered(statusEnum, null, null, null, null, null).stream()
                .collect(Collectors.groupingBy(ms -> ms.getOffice().getId()))
                .entrySet().stream()
                .map(entry -> {
                    Office office = entry.getValue().get(0).getOffice();
                    List<MailSendResponse> mailSends = entry.getValue().stream()
                            .map(ms -> MailSendResponse.from(ms, thisMonth))
                            .toList();
                    return new MailSendByOfficeResponse(OfficeResponse.from(office), mailSends);
                })
                .toList();
    }

    @Transactional
    public MailSendResponse createMailSend(CreateMailSendRequest req, String currentUserEmail) {
        LocalDate sendMonth = req.sendMonth().withDayOfMonth(1);
        if (mailSendRepository.existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
                req.userId(), req.officeId(), req.sendType(), sendMonth)) {
            throw new DuplicateResourceException("同じ送付レコードが既に存在します");
        }
        User user = userRepository.findById(req.userId())
                .orElseThrow(() -> new ResourceNotFoundException("利用者が見つかりません: " + req.userId()));
        Office office = officeRepository.findById(req.officeId())
                .orElseThrow(() -> new ResourceNotFoundException("事業所が見つかりません: " + req.officeId()));
        Staff createdBy = staffRepository.findByEmailIgnoreCase(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("スタッフが見つかりません: " + currentUserEmail));

        MailSend ms = new MailSend();
        ms.setUser(user);
        ms.setOffice(office);
        ms.setSendType(req.sendType());
        ms.setSendMonth(sendMonth);
        ms.setCreatedBy(createdBy);

        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        MailSendResponse result = MailSendResponse.from(mailSendRepository.save(ms), thisMonth);
        accessLogService.log("CREATE", "MAIL_SEND", result.id());
        return result;
    }

    @Transactional
    public MailSendResponse updateMailSend(Long id, CreateMailSendRequest req) {
        MailSend ms = findMailSendById(id);
        if (ms.getStatus() != SendStatus.PENDING) {
            throw new InvalidStatusException("PENDING以外のレコードは更新できません");
        }
        LocalDate sendMonth = req.sendMonth().withDayOfMonth(1);
        ms.setSendMonth(sendMonth);
        ms.setSendType(req.sendType());
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        MailSendResponse result = MailSendResponse.from(mailSendRepository.save(ms), thisMonth);
        accessLogService.log("UPDATE", "MAIL_SEND", id);
        return result;
    }

    @Transactional
    public void deleteMailSend(Long id) {
        MailSend ms = findMailSendById(id);
        if (ms.getStatus() != SendStatus.PENDING) {
            throw new InvalidStatusException("PENDING以外のレコードは削除できません");
        }
        mailSendRepository.delete(ms);
        accessLogService.log("DELETE", "MAIL_SEND", id);
    }

    private MailSend findMailSendById(Long id) {
        return mailSendRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("送付レコードが見つかりません: " + id));
    }

    private static final DateTimeFormatter SEND_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月");

    private SendStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return SendStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("ステータスの値が不正です: " + status);
        }
    }

    private LocalDate parseYearMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.isBlank()) return null;
        return LocalDate.parse(yearMonth + "-01");
    }

    private List<MailSendResponse> buildFilteredResponses(
            SendStatus statusEnum, LocalDate sendMonthDate, Long userId, Long officeId,
            LocalDate dateFromDate, LocalDate dateToDate) {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        return mailSendRepository.findFiltered(
                        statusEnum, sendMonthDate, userId, officeId, dateFromDate, dateToDate)
                .stream()
                .map(ms -> MailSendResponse.from(ms, thisMonth))
                .toList();
    }
}
