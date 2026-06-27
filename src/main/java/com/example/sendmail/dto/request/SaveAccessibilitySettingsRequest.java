package com.example.sendmail.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveAccessibilitySettingsRequest {
    private String fontSize;
    private Boolean furigana;
    private String bgColor;
}
