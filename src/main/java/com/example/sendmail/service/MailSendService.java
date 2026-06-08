package com.example.sendmail.service;

import com.example.sendmail.domain.entity.MailSend;
import com.example.sendmail.domain.entity.Office;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.domain.entity.User;
import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.dto.request.CreateMailSendRequest;
import com.example.sendmail.dto.response.MailSendByOfficeResponse;
import com.example.sendmail.dto.response.MailSendResponse;
import com.example.sendmail.dto.response.OfficeResponse;
import com.example.sendmail.exception.DuplicateResourceException;
import com.example.sendmail.exception.InvalidStatusException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.MailSendRepository;
import com.example.sendmail.repository.OfficeRepository;
import com.example.sendmail.repository.StaffRepository;
import com.example.sendmail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MailSendService {

    private final MailSendRepository mailSendRepository;
    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public List<MailSendResponse> listMailSends() {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        return mailSendRepository.findAll().stream()
                .map(ms -> MailSendResponse.from(ms, thisMonth))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MailSendByOfficeResponse> listByOffice() {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        return mailSendRepository.findAll().stream()
                .collect(Collectors.groupingBy(ms -> ms.getOffice().getId()))
                .entrySet().stream()
                .map(entry -> {
                    Office office = entry.getValue().get(0).getOffice();
                    List<MailSendResponse> mailSends = entry.getValue().stream()
                            .map(ms -> MailSendResponse.from(ms, thisMonth))
                            .toList();
                    return MailSendByOfficeResponse.builder()
                            .office(OfficeResponse.from(office))
                            .mailSends(mailSends)
                            .build();
                })
                .toList();
    }

    @Transactional
    public MailSendResponse createMailSend(CreateMailSendRequest req, String currentUserEmail) {
        LocalDate sendMonth = req.getSendMonth().withDayOfMonth(1);
        if (mailSendRepository.existsByUserIdAndOfficeIdAndSendTypeAndSendMonth(
                req.getUserId(), req.getOfficeId(), req.getSendType(), sendMonth)) {
            throw new DuplicateResourceException("同じ送付レコードが既に存在します");
        }
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("利用者が見つかりません: " + req.getUserId()));
        Office office = officeRepository.findById(req.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("事業所が見つかりません: " + req.getOfficeId()));
        Staff createdBy = staffRepository.findByEmailIgnoreCase(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("スタッフが見つかりません: " + currentUserEmail));

        MailSend ms = new MailSend();
        ms.setUser(user);
        ms.setOffice(office);
        ms.setSendType(req.getSendType());
        ms.setSendMonth(sendMonth);
        ms.setCreatedBy(createdBy);

        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        return MailSendResponse.from(mailSendRepository.save(ms), thisMonth);
    }

    @Transactional
    public MailSendResponse updateMailSend(Long id, CreateMailSendRequest req) {
        MailSend ms = findMailSendById(id);
        if (ms.getStatus() != SendStatus.PENDING) {
            throw new InvalidStatusException("PENDING以外のレコードは更新できません");
        }
        LocalDate sendMonth = req.getSendMonth().withDayOfMonth(1);
        ms.setSendMonth(sendMonth);
        ms.setSendType(req.getSendType());
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        return MailSendResponse.from(mailSendRepository.save(ms), thisMonth);
    }

    @Transactional
    public void deleteMailSend(Long id) {
        MailSend ms = findMailSendById(id);
        if (ms.getStatus() != SendStatus.PENDING) {
            throw new InvalidStatusException("PENDING以外のレコードは削除できません");
        }
        mailSendRepository.delete(ms);
    }

    private MailSend findMailSendById(Long id) {
        return mailSendRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("送付レコードが見つかりません: " + id));
    }
}
