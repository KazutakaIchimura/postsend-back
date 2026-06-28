package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateMailSendBatchRequest(
        @NotEmpty List<Long> mailSendIds,
        String notes
) {}
