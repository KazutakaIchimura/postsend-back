package com.example.sendmail.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOfficeRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 8)
    private String postalCode;

    @Size(max = 200)
    private String building;

    @Size(max = 500)
    private String address;

    @Size(max = 20)
    private String phone;
}
