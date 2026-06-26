package com.example.sendmail.controller;

import com.example.sendmail.dto.response.AccessLogResponse;
import com.example.sendmail.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access-logs")
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogService accessLogService;

    @GetMapping
    public Page<AccessLogResponse> listAccessLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return accessLogService.findAll(PageRequest.of(page, Math.min(size, 200)))
                .map(AccessLogResponse::from);
    }
}
