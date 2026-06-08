package com.example.sendmail.dto.request;

import com.example.sendmail.domain.enums.SendType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateMailSendRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Long officeId;

    @NotNull
    private SendType sendType;

    @NotNull
    private LocalDate sendMonth;
}
