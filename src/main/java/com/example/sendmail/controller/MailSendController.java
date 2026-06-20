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

    @GetMapping("/by-office")
    public List<MailSendByOfficeResponse> listByOffice(
            @RequestParam(required = false) String status) {
        return mailSendService.listByOffice(status);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long officeId) {
        byte[] csv = mailSendService.exportCsv(dateFrom, dateTo, userId, officeId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mail-sends.csv\"")
                .body(csv);
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
