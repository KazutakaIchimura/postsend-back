package com.example.sendmail.dto.request;

public record SaveAccessibilitySettingsRequest(
        String fontSize,
        Boolean furigana,
        String bgColor
) {}
