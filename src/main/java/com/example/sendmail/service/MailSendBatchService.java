package com.example.sendmail.service;

import com.example.sendmail.domain.entity.MailSend;
import com.example.sendmail.domain.entity.MailSendBatch;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.domain.enums.SendStatus;
import com.example.sendmail.dto.request.CreateMailSendBatchRequest;
import com.example.sendmail.dto.response.MailSendBatchResponse;
import com.example.sendmail.exception.InvalidStatusException;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.MailSendBatchRepository;
import com.example.sendmail.repository.MailSendRepository;
import com.example.sendmail.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MailSendBatchService {

    private final MailSendBatchRepository mailSendBatchRepository;
    private final MailSendRepository mailSendRepository;
    private final StaffRepository staffRepository;
    private final AccessLogService accessLogService;

    @Transactional
    public MailSendBatchResponse createBatch(CreateMailSendBatchRequest req, String currentUserEmail) {
        Staff sentBy = staffRepository.findByEmailIgnoreCase(currentUserEmail)
                .orElseThrow(() -> new ResourceNotFoundException("スタッフが見つかりません: " + currentUserEmail));

        Set<Long> requestedIds = new LinkedHashSet<>(req.mailSendIds());
        List<MailSend> mailSends = mailSendRepository.findAllById(requestedIds);
        if (mailSends.size() != requestedIds.size()) {
            throw new ResourceNotFoundException("存在しない送付レコードが含まれています");
        }
        boolean hasNonPending = mailSends.stream().anyMatch(ms -> ms.getStatus() != SendStatus.PENDING);
        if (hasNonPending) {
            throw new InvalidStatusException("PENDING以外のレコードが含まれています");
        }

        MailSendBatch batch = new MailSendBatch();
        batch.setSentBy(sentBy);
        batch.setSentAt(LocalDateTime.now());
        batch.setNotes(req.notes());
        MailSendBatch savedBatch = mailSendBatchRepository.save(batch);

        mailSends.forEach(ms -> {
            ms.setStatus(SendStatus.SENT);
            ms.setBatch(savedBatch);
        });
        mailSendRepository.saveAll(mailSends);

        accessLogService.log("CREATE", "BATCH", savedBatch.getId());
        return new MailSendBatchResponse(
                savedBatch.getId(),
                savedBatch.getSentAt(),
                mailSends.size(),
                savedBatch.getNotes()
        );
    }

    @Transactional(readOnly = true)
    public MailSendBatchResponse getBatch(Long id) {
        MailSendBatch batch = mailSendBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("バッチが見つかりません: " + id));
        long count = mailSendRepository.countByBatchId(id);
        return new MailSendBatchResponse(
                batch.getId(),
                batch.getSentAt(),
                (int) count,
                batch.getNotes()
        );
    }
}
