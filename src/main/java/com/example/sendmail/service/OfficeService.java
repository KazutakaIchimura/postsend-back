package com.example.sendmail.service;

import com.example.sendmail.domain.entity.Office;
import com.example.sendmail.dto.request.CreateOfficeRequest;
import com.example.sendmail.dto.request.UpdateOfficeRequest;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.OfficeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfficeService {

    private final OfficeRepository officeRepository;
    private final AccessLogService accessLogService;

    @Transactional(readOnly = true)
    public List<OfficeResponse> listOffices(boolean includeInactive) {
        List<Office> offices = includeInactive
                ? officeRepository.findAll()
                : officeRepository.findByIsActiveTrue();
        return offices.stream().map(OfficeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OfficeResponse getOffice(Long id) {
        return OfficeResponse.from(findOfficeById(id));
    }

    @Transactional
    public OfficeResponse createOffice(CreateOfficeRequest req) {
        Office office = new Office();
        office.setName(req.getName());
        office.setPostalCode(req.getPostalCode());
        office.setBuilding(req.getBuilding());
        office.setAddress(req.getAddress());
        office.setPhone(req.getPhone());
        OfficeResponse result = OfficeResponse.from(officeRepository.save(office));
        accessLogService.log("CREATE", "OFFICE", result.getId());
        return result;
    }

    @Transactional
    public OfficeResponse updateOffice(Long id, UpdateOfficeRequest req) {
        Office office = findOfficeById(id);
        office.setName(req.getName());
        office.setPostalCode(req.getPostalCode());
        office.setBuilding(req.getBuilding());
        office.setAddress(req.getAddress());
        office.setPhone(req.getPhone());
        OfficeResponse result = OfficeResponse.from(officeRepository.save(office));
        accessLogService.log("UPDATE", "OFFICE", id);
        return result;
    }

    @Transactional
    public void deactivateOffice(Long id) {
        Office office = findOfficeById(id);
        office.setIsActive(false);
        officeRepository.save(office);
        accessLogService.log("DEACTIVATE", "OFFICE", id);
    }

    @Transactional
    public OfficeResponse activateOffice(Long id) {
        Office office = findOfficeById(id);
        office.setIsActive(true);
        OfficeResponse result = OfficeResponse.from(officeRepository.save(office));
        accessLogService.log("ACTIVATE", "OFFICE", id);
        return result;
    }

    private Office findOfficeById(Long id) {
        return officeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("事業所が見つかりません: " + id));
    }
}
