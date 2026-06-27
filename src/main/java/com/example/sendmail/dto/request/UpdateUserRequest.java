package com.example.sendmail.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Optional;

@Getter
@Setter
public class UpdateUserRequest {

    @Size(max = 100)
    private String name;

    // null=フィールド未送信（変更しない）、Optional.empty()=明示的null送信（クリア）
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Optional<@Size(max = 100, message = "ふりがなは100文字以内で入力してください") String> nameKana = null;

    // birthDate のクリアは仕様上不要。null=未送信（変更しない）として扱う。
    @Past
    private LocalDate birthDate;

    // null=フィールド未送信（変更しない）、Optional.empty()=明示的null送信（クリア）
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Optional<@Size(max = 2000, message = "notesは2000文字以内で入力してください") String> notes = null;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Optional<@Size(max = 20) String> recipientNumber = null;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Optional<@Size(max = 20) String> disabilitySupportCategory = null;

    // null=未送信（変更しない）、Optional.empty()=担当者クリア、Optional.of(id)=担当者設定
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    private Optional<Long> assignedStaffId = null;
}
