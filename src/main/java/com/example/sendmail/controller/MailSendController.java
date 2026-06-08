package com.example.sendmail.controller;

import com.example.sendmail.dto.request.CreateMailSendRequest;
import com.example.sendmail.dto.response.MailSendByOfficeResponse;
import com.example.sendmail.dto.response.MailSendResponse;
import com.example.sendmail.service.MailSendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mail-sends")
@RequiredArgsConstructor
public class MailSendController {

    private final MailSendService mailSendService;

    @GetMapping
    public List<MailSendResponse> listMailSends() {
        return mailSendService.listMailSends();
    }

    @GetMapping("/by-office")
    public List<MailSendByOfficeResponse> listByOffice() {
        return mailSendService.listByOffice();
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
