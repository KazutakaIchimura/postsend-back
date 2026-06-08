package com.example.sendmail.controller;

import com.example.sendmail.dto.request.CreateMailSendBatchRequest;
import com.example.sendmail.dto.response.MailSendBatchResponse;
import com.example.sendmail.service.MailSendBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mail-send-batches")
@RequiredArgsConstructor
public class MailSendBatchController {

    private final MailSendBatchService mailSendBatchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MailSendBatchResponse createBatch(@Valid @RequestBody CreateMailSendBatchRequest req,
                                             Authentication auth) {
        return mailSendBatchService.createBatch(req, auth.getName());
    }

    @GetMapping("/{id}")
    public MailSendBatchResponse getBatch(@PathVariable Long id) {
        return mailSendBatchService.getBatch(id);
    }
}
