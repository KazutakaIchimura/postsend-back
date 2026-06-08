package com.example.sendmail.controller;

import com.example.sendmail.dto.request.CreateStaffRequest;
import com.example.sendmail.dto.request.UpdateStaffRequest;
import com.example.sendmail.dto.response.StaffResponse;
import com.example.sendmail.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staffs")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    public List<StaffResponse> listStaffs(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return staffService.listStaffs(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffResponse createStaff(@Valid @RequestBody CreateStaffRequest req) {
        return staffService.createStaff(req);
    }

    @PutMapping("/{id}")
    public StaffResponse updateStaff(@PathVariable Long id,
                                     @Valid @RequestBody UpdateStaffRequest req) {
        return staffService.updateStaff(id, req);
    }

    @PatchMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activateStaff(@PathVariable Long id) {
        staffService.activateStaff(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateStaff(@PathVariable Long id) {
        staffService.deactivateStaff(id);
    }
}
