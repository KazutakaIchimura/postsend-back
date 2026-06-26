package com.example.sendmail.service;

import com.example.sendmail.domain.entity.User;
import com.example.sendmail.dto.request.UpdateUserRequest;
import com.example.sendmail.dto.response.UserResponse;
import com.example.sendmail.repository.OfficeRepository;
import com.example.sendmail.repository.UserOfficeRepository;
import com.example.sendmail.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OfficeRepository officeRepository;
    @Mock
    private UserOfficeRepository userOfficeRepository;
    @Mock
    private AccessLogService accessLogService;

    @InjectMocks
    private UserService userService;

    private User existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new User();
        existingUser.setId(1L);
        existingUser.setName("山田太郎");
        existingUser.setNameKana("やまだたろう");
        existingUser.setIsActive(true);
        existingUser.setCreatedAt(LocalDateTime.now());
        existingUser.setUpdatedAt(LocalDateTime.now());

        when(userRepository.findById(1L)).thenReturn(Optional.of(existingUser));
    }

    @Test
    @DisplayName("updateUser: Optional.empty()をnameKanaに渡すとnullがセットされる（クリア）")
    void updateUser_nameKanaEmptyOptional_clearsNameKana() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setName("山田太郎");
        req.setNameKana(Optional.empty()); // 明示的null送信

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.updateUser(1L, req);

        assertThat(captor.getValue().getNameKana()).isNull();
        assertThat(result.getName()).isEqualTo("山田太郎");
    }

    @Test
    @DisplayName("updateUser: nameKanaをnull（未送信）にすると既存値が保持される")
    void updateUser_nameKanaNull_keepsExistingValue() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setName("山田次郎");
        // req.setNameKana を呼ばない → null（未送信）

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(1L, req);

        assertThat(captor.getValue().getNameKana()).isEqualTo("やまだたろう");
    }

    @Test
    @DisplayName("updateUser: nameKanaに値を渡すと更新される")
    void updateUser_nameKanaWithValue_updatesNameKana() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setName("山田太郎");
        req.setNameKana(Optional.of("やまだじろう")); // 値更新

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(1L, req);

        assertThat(captor.getValue().getNameKana()).isEqualTo("やまだじろう");
    }
}
