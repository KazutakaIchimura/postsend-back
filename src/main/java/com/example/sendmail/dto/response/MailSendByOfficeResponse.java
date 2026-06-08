package com.example.sendmail.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MailSendByOfficeResponse {
    private OfficeResponse office;
    private List<MailSendResponse> mailSends;
}
