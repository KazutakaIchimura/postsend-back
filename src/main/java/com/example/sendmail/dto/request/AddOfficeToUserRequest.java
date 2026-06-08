package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddOfficeToUserRequest {

    @NotNull
    @Positive
    private Long officeId;
}
