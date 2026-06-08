package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStaffRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    private String role;

    @Size(min = 8, max = 100)
    private String password;
}
