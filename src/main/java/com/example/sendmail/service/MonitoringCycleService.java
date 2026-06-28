package com.example.sendmail.service;

import com.example.sendmail.domain.entity.MonitoringCycle;
import com.example.sendmail.domain.entity.Staff;
import com.example.sendmail.domain.entity.User;
import com.example.sendmail.dto.request.SaveMonitoringCycleRequest;
import com.example.sendmail.dto.response.MonitoringCycleResponse;
import com.example.sendmail.exception.ResourceNotFoundException;
import com.example.sendmail.repository.MonitoringCycleRepository;
import com.example.sendmail.repository.StaffRepository;
import com.example.sendmail.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitoringCycleService {

    private final MonitoringCycleRepository monitoringCycleRepository;
    private final UserRepository userRepository;
    private final StaffRepository staffRepository;

    @Transactional(readOnly = true)
    public List<MonitoringCycleResponse> listSchedule() {
        var cycleByUserId = monitoringCycleRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        mc -> mc.getUser().getId(),
                        mc -> mc
                ));

        return userRepository.findAllActiveWithAssignedStaff().stream()
                .map(user -> {
                    MonitoringCycle mc = cycleByUserId.get(user.getId());
                    return mc != null
                            ? MonitoringCycleResponse.from(mc)
                            : MonitoringCycleResponse.fromUser(user);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoringCycleResponse getByUserId(Long userId) {
        MonitoringCycle mc = monitoringCycleRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("モニタリング設定が見つかりません: userId=" + userId));
        return MonitoringCycleResponse.from(mc);
    }

    @Transactional
    public MonitoringCycleResponse save(Long userId, SaveMonitoringCycleRequest req, String currentStaffEmail, boolean isAdmin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("利用者が見つかりません: " + userId));

        if (!isAdmin) {
            Staff currentStaff = staffRepository.findByEmailIgnoreCase(currentStaffEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("スタッフが見つかりません: " + currentStaffEmail));
            Staff assignedStaff = user.getAssignedStaff();
            if (assignedStaff == null || !assignedStaff.getId().equals(currentStaff.getId())) {
                throw new AccessDeniedException("担当として割り当てられていない利用者のモニタリング設定は変更できません");
            }
        }

        MonitoringCycle mc = monitoringCycleRepository.findByUserId(userId)
                .orElseGet(() -> {
                    MonitoringCycle newMc = new MonitoringCycle();
                    newMc.setUser(user);
                    return newMc;
                });

        mc.setCycleMonths(req.cycleMonths());
        mc.setNextMonitoringDate(req.nextMonitoringDate());
        mc.setNextPlanDraftDate(req.nextPlanDraftDate());
        mc.setNextPlanDate(req.nextPlanDate());
        mc.setNotes(req.notes());

        return MonitoringCycleResponse.from(monitoringCycleRepository.save(mc));
    }
}
