package com.example.sendmail.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Optional;

// null=フィールド未送信（変更しない）、Optional.empty()=明示的null送信（クリア）
public record UpdateUserRequest(
        @Size(max = 100) String name,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<@Size(max = 100, message = "ふりがなは100文字以内で入力してください") String> nameKana,
        // birthDate のクリアは仕様上不要。null=未送信（変更しない）として扱う。
        @Past LocalDate birthDate,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<@Size(max = 2000, message = "notesは2000文字以内で入力してください") String> notes,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<@Size(max = 20) String> recipientNumber,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<@Size(max = 20) String> disabilitySupportCategory,
        @JsonSetter(nulls = Nulls.AS_EMPTY) Optional<Long> assignedStaffId
) {}
