package com.example.sendmail.service;

import com.example.sendmail.domain.entity.Office;
import com.example.sendmail.domain.entity.User;
import com.example.sendmail.domain.entity.UserOffice;
import com.example.sendmail.dto.request.CreateUserRequest;
import com.example.sendmail.dto.request.UpdateUserRequest;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.dto.response.UserResponse;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.OfficeRepository;
import com.example.sendmail.repository.UserOfficeRepository;
import com.example.sendmail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;
    private final UserOfficeRepository userOfficeRepository;
    private final AccessLogService accessLogService;

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(boolean includeInactive) {
        Pageable limit = PageRequest.of(0, 1000, Sort.by("id"));
        List<User> users = includeInactive
                ? userRepository.findAll(limit).getContent()
                : userRepository.findByIsActiveTrue(limit).getContent();
        return users.stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        User user = findUserById(id);
        List<OfficeResponse> offices = userOfficeRepository.findByUserIdWithOffice(id).stream()
                .map(uo -> OfficeResponse.from(uo.getOffice()))
                .toList();
        accessLogService.log("VIEW", "USER", id);
        return UserResponse.fromWithOffices(user, offices);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest req) {
        User user = new User();
        user.setName(req.getName());
        user.setNameKana(req.getNameKana());
        user.setBirthDate(req.getBirthDate());
        user.setNotes(req.getNotes());
        user.setIsActive(true);
        UserResponse result = UserResponse.from(userRepository.save(user));
        accessLogService.log("CREATE", "USER", result.getId());
        return result;
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest req) {
        if (req.getName() == null && req.getNameKana() == null
                && req.getBirthDate() == null && req.getNotes() == null) {
            throw new IllegalStateException("更新するフィールドが指定されていません");
        }
        User user = findUserById(id);
        if (req.getName() != null) {
            if (req.getName().isBlank()) throw new IllegalArgumentException("名前は空白のみでは設定できません");
            user.setName(req.getName());
        }
        if (req.getNameKana() != null) user.setNameKana(req.getNameKana());
        if (req.getBirthDate() != null) user.setBirthDate(req.getBirthDate());
        if (req.getNotes() != null) user.setNotes(req.getNotes().orElse(null));
        UserResponse result = UserResponse.from(userRepository.save(user));
        accessLogService.log("UPDATE", "USER", id);
        return result;
    }

    @Transactional
    public UserResponse deactivateUser(Long id) {
        User user = findUserById(id);
        user.setIsActive(false);
        UserResponse result = UserResponse.from(userRepository.save(user));
        accessLogService.log("DEACTIVATE", "USER", id);
        return result;
    }

    @Transactional
    public UserResponse activateUser(Long id) {
        User user = findUserById(id);
        user.setIsActive(true);
        UserResponse result = UserResponse.from(userRepository.save(user));
        accessLogService.log("ACTIVATE", "USER", id);
        return result;
    }

    @Transactional(readOnly = true)
    public List<OfficeResponse> getUserOffices(Long userId) {
        findUserById(userId);
        return userOfficeRepository.findByUserIdWithOffice(userId).stream()
                .map(uo -> OfficeResponse.from(uo.getOffice()))
                .toList();
    }

    @Transactional
    public UserResponse addOfficeToUser(Long userId, Long officeId) {
        User user = findUserById(userId);
        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new ResourceNotFoundException("事業所が見つかりません: " + officeId));
        UserOffice uo = new UserOffice();
        uo.setUser(user);
        uo.setOffice(office);
        userOfficeRepository.saveAndFlush(uo);
        List<OfficeResponse> offices = userOfficeRepository.findByUserIdWithOffice(userId).stream()
                .map(o -> OfficeResponse.from(o.getOffice()))
                .toList();
        accessLogService.log("ADD_OFFICE", "USER", userId);
        return UserResponse.fromWithOffices(user, offices);
    }

    @Transactional
    public void removeOfficeFromUser(Long userId, Long officeId) {
        findUserById(userId);
        UserOffice uo = userOfficeRepository.findByUserIdAndOfficeId(userId, officeId)
                .orElseThrow(() -> new ResourceNotFoundException("紐付けが見つかりません"));
        userOfficeRepository.delete(uo);
        accessLogService.log("REMOVE_OFFICE", "USER", userId);
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("利用者が見つかりません: " + id));
    }
}
