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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MailSendService {

    private final MailSendRepository mailSendRepository;
    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;
    private final StaffRepository staffRepository;

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

        return mailSendRepository.findAll().stream()
                .filter(ms -> statusEnum == null || ms.getStatus() == statusEnum)
                .collect(Collectors.groupingBy(ms -> ms.getOffice().getId()))
                .entrySet().stream()
                .map(entry -> {
                    Office office = entry.getValue().get(0).getOffice();
                    List<MailSendResponse> mailSends = entry.getValue().stream()
                            .map(ms -> MailSendResponse.from(ms, thisMonth))
                            .toList();
                    return MailSendByOfficeResponse.builder()
                            .office(OfficeResponse.from(office))
                            .mailSends(mailSends)
                            .build();
                })
                .toList();
    }

    @Transactional
    public MailSendResponse createMailSend(CreateMailSendRequest req, String currentUserEmail) {
        LocalDate sendMonth = req.getSendMonth().withDayOfMonth(1);
        if (mailSendRepository.existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
                req.getUserId(), req.getOfficeId(), req.getSendType(), sendMonth)) {
            throw new DuplicateResourceException("同じ送付レコードが既に存在します");
        }
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("利用者が見つかりません: " + req.getUserId()));
        Office office = officeRepository.findById(req.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("事業所が見つかりません: " + req.getOfficeId()));
        Staff createdBy = staffRepository.findByEmailIgnoreCase(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("スタッフが見つかりません: " + currentUserEmail));

        MailSend ms = new MailSend();
        ms.setUser(user);
        ms.setOffice(office);
        ms.setSendType(req.getSendType());
        ms.setSendMonth(sendMonth);
        ms.setCreatedBy(createdBy);

        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        return MailSendResponse.from(mailSendRepository.save(ms), thisMonth);
    }

    @Transactional
    public MailSendResponse updateMailSend(Long id, CreateMailSendRequest req) {
        MailSend ms = findMailSendById(id);
        if (ms.getStatus() != SendStatus.PENDING) {
            throw new InvalidStatusException("PENDING以外のレコードは更新できません");
        }
        LocalDate sendMonth = req.getSendMonth().withDayOfMonth(1);
        ms.setSendMonth(sendMonth);
        ms.setSendType(req.getSendType());
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        return MailSendResponse.from(mailSendRepository.save(ms), thisMonth);
    }

    @Transactional
    public void deleteMailSend(Long id) {
        MailSend ms = findMailSendById(id);
        if (ms.getStatus() != SendStatus.PENDING) {
            throw new InvalidStatusException("PENDING以外のレコードは削除できません");
        }
        mailSendRepository.delete(ms);
    }

    private MailSend findMailSendById(Long id) {
        return mailSendRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("送付レコードが見つかりません: " + id));
    }

    private static final DateTimeFormatter SEND_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月");
    private static final String CSV_HEADER = "送付月,利用者名,事業所名,種別,ステータス,更新日時\r\n";

    @Transactional(readOnly = true)
    public byte[] exportCsv(String dateFrom, String dateTo, Long userId, Long officeId) {
        List<MailSendResponse> records = buildFilteredResponses(
                null, null, userId, officeId,
                parseYearMonth(dateFrom), parseYearMonth(dateTo));

        StringBuilder sb = new StringBuilder(CSV_HEADER);
        for (MailSendResponse ms : records) {
            String sendTypeLabel = switch (ms.getSendType()) {
                case PLAN -> "計画作成";
                case MONITORING -> "モニタリング";
            };
            sb.append(ms.getSendMonth() != null ? SEND_MONTH_FORMATTER.format(ms.getSendMonth()) : "").append(",")
              .append(csvQuote(ms.getUserName())).append(",")
              .append(csvQuote(ms.getOfficeName())).append(",")
              .append(sendTypeLabel).append(",")
              .append(csvStatusLabel(ms.getStatus())).append(",")
              .append(ms.getUpdatedAt() != null ? ms.getUpdatedAt().toString().replace("T", " ").substring(0, 19) : "")
              .append("\r\n");
        }

        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(content, 0, result, bom.length, content.length);
        return result;
    }

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
        try {
            return LocalDate.parse(yearMonth + "-01");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日付の形式が正しくありません（例: 2026-06）: " + yearMonth);
        }
    }

    private List<MailSendResponse> buildFilteredResponses(
            SendStatus statusEnum, LocalDate sendMonthDate, Long userId, Long officeId,
            LocalDate dateFromDate, LocalDate dateToDate) {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        return mailSendRepository.findAll().stream()
                .filter(ms -> statusEnum == null || ms.getStatus() == statusEnum)
                .filter(ms -> sendMonthDate == null || ms.getSendMonth().equals(sendMonthDate))
                .filter(ms -> userId == null || ms.getUser().getId().equals(userId))
                .filter(ms -> officeId == null || ms.getOffice().getId().equals(officeId))
                .filter(ms -> dateFromDate == null || !ms.getSendMonth().isBefore(dateFromDate))
                .filter(ms -> dateToDate == null || !ms.getSendMonth().isAfter(dateToDate))
                .map(ms -> MailSendResponse.from(ms, thisMonth))
                .toList();
    }

    private String csvQuote(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\r") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String csvStatusLabel(SendStatus status) {
        if (status == null) return "";
        return switch (status) {
            case PENDING -> "送付待ち";
            case SENT -> "送付済み";
            case DONE -> "完了";
        };
    }
}
