package com.example.sendmail.controller;

import com.example.sendmail.dto.request.CreateMailSendRequest;
import com.example.sendmail.dto.response.MailSendByOfficeResponse;
import com.example.sendmail.dto.response.MailSendResponse;
import com.example.sendmail.service.MailSendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/mail-sends")
@RequiredArgsConstructor
public class MailSendController {

    private final MailSendService mailSendService;

    @GetMapping
    public List<MailSendResponse> listMailSends(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sendMonth,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        return mailSendService.listMailSends(status, sendMonth, userId, officeId, dateFrom, dateTo);
    }

    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sendMonth,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long officeId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        List<MailSendResponse> mailSends = mailSendService.listMailSends(status, sendMonth, userId, officeId, dateFrom, dateTo);
        String csv = buildCsv(mailSends);
        byte[] bytes = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mail-sends.csv\"")
                .body(bytes);
    }

    private static final DateTimeFormatter SEND_MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private String buildCsv(List<MailSendResponse> mailSends) {
        StringBuilder sb = new StringBuilder("利用者名,事業所名,送付種別,送付月,ステータス,送付日時\n");
        for (MailSendResponse ms : mailSends) {
            sb.append(csvEscape(ms.getUserName())).append(',');
            sb.append(csvEscape(ms.getOfficeName())).append(',');
            sb.append(ms.getSendType() == com.example.sendmail.domain.enums.SendType.PLAN ? "計画作成" : "モニタリング").append(',');
            sb.append(ms.getSendMonth() != null ? ms.getSendMonth().format(SEND_MONTH_FMT) : "").append(',');
            sb.append(ms.getStatus() == com.example.sendmail.domain.enums.SendStatus.SENT ? "送付済" : "未送付").append(',');
            sb.append(ms.getUpdatedAt() != null ? ms.getUpdatedAt().toString().replace("T", " ").substring(0, 16) : "");
            sb.append('\n');
        }
        return sb.toString();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @GetMapping("/by-office")
    public List<MailSendByOfficeResponse> listByOffice(
            @RequestParam(required = false) String status) {
        return mailSendService.listByOffice(status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MailSendResponse createMailSend(@Valid @RequestBody CreateMailSendRequest req,
                                           Authentication auth) {
        return mailSendService.createMailSend(req, auth.getName());
    }

    @PutMapping("/{id}")
    public MailSendResponse updateMailSend(@PathVariable Long id,
                                           @Valid @RequestBody CreateMailSendRequest req) {
        return mailSendService.updateMailSend(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMailSend(@PathVariable Long id) {
        mailSendService.deleteMailSend(id);
    }
}
