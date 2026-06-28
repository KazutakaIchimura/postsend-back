package com.example.sendmail.dto.response;

import java.util.List;

public record MailSendByOfficeResponse(OfficeResponse office, List<MailSendResponse> mailSends) {}
