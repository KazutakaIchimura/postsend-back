package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateMailSendBatchRequest {

    @NotEmpty
    private List<Long> mailSendIds;

    private String notes;
}
