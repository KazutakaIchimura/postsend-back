package com.example.sendmail.controller;

import com.example.sendmail.dto.request.AddOfficeToUserRequest;
import com.example.sendmail.dto.request.CreateUserRequest;
import com.example.sendmail.dto.request.UpdateUserRequest;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.dto.response.UserResponse;
import com.example.sendmail.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponse> listUsers(
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Authentication authentication) {
        if (includeInactive) {
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new AccessDeniedException("ADMIN権限が必要です");
            }
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!isAdmin) throw new AccessDeniedException("ADMIN権限が必要です");
        }
        return userService.listUsers(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest req) {
        return userService.createUser(req);
    }

    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(@PathVariable Long id,
                                   @Valid @RequestBody UpdateUserRequest req) {
        return userService.updateUser(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse activateUser(@PathVariable Long id) {
        return userService.activateUser(id);
    }

    @GetMapping("/{userId}/offices")
    public List<OfficeResponse> getUserOffices(@PathVariable Long userId) {
        return userService.getUserOffices(userId);
    }

    @PostMapping("/{userId}/offices")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse addOfficeToUser(@PathVariable Long userId,
                                        @Valid @RequestBody AddOfficeToUserRequest req) {
        return userService.addOfficeToUser(userId, req.getOfficeId());
    }

    @DeleteMapping("/{userId}/offices/{officeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void removeOfficeFromUser(@PathVariable Long userId,
                                     @PathVariable Long officeId) {
        userService.removeOfficeFromUser(userId, officeId);
    }
}
