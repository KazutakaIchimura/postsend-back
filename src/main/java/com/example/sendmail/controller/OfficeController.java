package com.example.sendmail.controller;

import com.example.sendmail.dto.request.CreateOfficeRequest;
import com.example.sendmail.dto.request.UpdateOfficeRequest;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.service.OfficeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/offices")
@RequiredArgsConstructor
public class OfficeController {

    private final OfficeService officeService;

    @GetMapping
    public List<OfficeResponse> listOffices(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return officeService.listOffices(includeInactive);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfficeResponse createOffice(@Valid @RequestBody CreateOfficeRequest req) {
        return officeService.createOffice(req);
    }

    @GetMapping("/{id}")
    public OfficeResponse getOffice(@PathVariable Long id) {
        return officeService.getOffice(id);
    }

    @PutMapping("/{id}")
    public OfficeResponse updateOffice(@PathVariable Long id,
                                       @Valid @RequestBody UpdateOfficeRequest req) {
        return officeService.updateOffice(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateOffice(@PathVariable Long id) {
        officeService.deactivateOffice(id);
    }

    @PatchMapping("/{id}/activate")
    public OfficeResponse activateOffice(@PathVariable Long id) {
        return officeService.activateOffice(id);
    }
}
