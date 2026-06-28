package com.example.sendmail.service;

import com.example.sendmail.domain.entity.Office;
import com.example.sendmail.dto.request.CreateOfficeRequest;
import com.example.sendmail.dto.request.UpdateOfficeRequest;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.OfficeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OfficeService")
class OfficeServiceTest {

    @Mock
    private OfficeRepository officeRepository;

    @Mock
    private AccessLogService accessLogService;

    @InjectMocks
    private OfficeService officeService;

    private Office activeOffice;
    private Office inactiveOffice;

    @BeforeEach
    void setUp() {
        activeOffice = new Office();
        activeOffice.setId(1L);
        activeOffice.setName("中央事業所");
        activeOffice.setPostalCode("100-0001");
        activeOffice.setAddress("東京都千代田区1-1-1");
        activeOffice.setIsActive(true);

        inactiveOffice = new Office();
        inactiveOffice.setId(2L);
        inactiveOffice.setName("廃止事業所");
        inactiveOffice.setIsActive(false);
    }

    // ============================================================
    // listOffices
    // ============================================================

    @Test
    @DisplayName("listOffices(false): findByIsActiveTrueを呼び有効事業所のみ返す")
    void listOffices_excludeInactive_callsFindByIsActiveTrue() {
        when(officeRepository.findByIsActiveTrue()).thenReturn(List.of(activeOffice));

        List<OfficeResponse> result = officeService.listOffices(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        verify(officeRepository).findByIsActiveTrue();
        verify(officeRepository, never()).findAll();
    }

    @Test
    @DisplayName("listOffices(true): findAllを呼び無効事業所も含めて返す")
    void listOffices_includeInactive_callsFindAll() {
        when(officeRepository.findAll()).thenReturn(List.of(activeOffice, inactiveOffice));

        List<OfficeResponse> result = officeService.listOffices(true);

        assertThat(result).hasSize(2);
        verify(officeRepository).findAll();
        verify(officeRepository, never()).findByIsActiveTrue();
    }

    // ============================================================
    // getOffice
    // ============================================================

    @Test
    @DisplayName("getOffice: 存在するIDで詳細が返る")
    void getOffice_existingId_returnsResponse() {
        when(officeRepository.findById(1L)).thenReturn(Optional.of(activeOffice));

        OfficeResponse result = officeService.getOffice(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("中央事業所");
    }

    @Test
    @DisplayName("getOffice: 存在しないIDでResourceNotFoundExceptionをスロー")
    void getOffice_nonExistingId_throwsResourceNotFoundException() {
        when(officeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> officeService.getOffice(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("事業所が見つかりません: 999");
    }

    // ============================================================
    // createOffice
    // ============================================================

    @Test
    @DisplayName("createOffice: リクエストのフィールドが正しくOfficeエンティティに設定されて保存される")
    void createOffice_setsFieldsAndSaves() {
        CreateOfficeRequest req = new CreateOfficeRequest("新事業所", "就労継続支援A型", "123-4567", null, "東京都新宿区1-1", "03-0000-0001");

        Office saved = new Office();
        saved.setId(3L);
        saved.setName("新事業所");
        saved.setOfficeType("就労継続支援A型");
        saved.setIsActive(true);

        ArgumentCaptor<Office> captor = ArgumentCaptor.forClass(Office.class);
        when(officeRepository.save(captor.capture())).thenReturn(saved);

        OfficeResponse result = officeService.createOffice(req);

        assertThat(result.id()).isEqualTo(3L);
        assertThat(captor.getValue().getName()).isEqualTo("新事業所");
        assertThat(captor.getValue().getOfficeType()).isEqualTo("就労継続支援A型");
        assertThat(captor.getValue().getPostalCode()).isEqualTo("123-4567");
        verify(accessLogService).log("CREATE", "OFFICE", 3L);
    }

    // ============================================================
    // updateOffice
    // ============================================================

    @Test
    @DisplayName("updateOffice: リクエストのフィールドが既存エンティティに上書きされる")
    void updateOffice_updatesFields() {
        UpdateOfficeRequest req = new UpdateOfficeRequest("中央事業所（改称）", "就労継続支援B型", null, null, null, null);

        when(officeRepository.findById(1L)).thenReturn(Optional.of(activeOffice));
        when(officeRepository.save(activeOffice)).thenReturn(activeOffice);

        officeService.updateOffice(1L, req);

        assertThat(activeOffice.getName()).isEqualTo("中央事業所（改称）");
        assertThat(activeOffice.getOfficeType()).isEqualTo("就労継続支援B型");
        verify(accessLogService).log("UPDATE", "OFFICE", 1L);
    }

    @Test
    @DisplayName("updateOffice: 存在しないIDでResourceNotFoundExceptionをスロー")
    void updateOffice_nonExistingId_throws() {
        when(officeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> officeService.updateOffice(999L, new UpdateOfficeRequest(null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // deactivateOffice
    // ============================================================

    @Test
    @DisplayName("deactivateOffice: isActiveがfalseに設定されて保存される")
    void deactivateOffice_setsIsActiveFalseAndSaves() {
        when(officeRepository.findById(1L)).thenReturn(Optional.of(activeOffice));
        when(officeRepository.save(activeOffice)).thenReturn(activeOffice);

        officeService.deactivateOffice(1L);

        assertThat(activeOffice.getIsActive()).isFalse();
        verify(officeRepository).save(activeOffice);
        verify(accessLogService).log("DEACTIVATE", "OFFICE", 1L);
    }

    @Test
    @DisplayName("deactivateOffice: 存在しないIDでResourceNotFoundExceptionをスロー")
    void deactivateOffice_nonExistingId_throws() {
        when(officeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> officeService.deactivateOffice(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============================================================
    // activateOffice
    // ============================================================

    @Test
    @DisplayName("activateOffice: isActiveがtrueに設定されて保存される")
    void activateOffice_setsIsActiveTrueAndSaves() {
        when(officeRepository.findById(2L)).thenReturn(Optional.of(inactiveOffice));
        when(officeRepository.save(inactiveOffice)).thenReturn(inactiveOffice);

        OfficeResponse result = officeService.activateOffice(2L);

        assertThat(inactiveOffice.getIsActive()).isTrue();
        verify(officeRepository).save(inactiveOffice);
        verify(accessLogService).log("ACTIVATE", "OFFICE", 2L);
    }

    @Test
    @DisplayName("activateOffice: 存在しないIDでResourceNotFoundExceptionをスロー")
    void activateOffice_nonExistingId_throws() {
        when(officeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> officeService.activateOffice(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
