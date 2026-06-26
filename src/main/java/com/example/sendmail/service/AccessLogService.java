package com.example.sendmail.service;

import com.example.sendmail.domain.entity.AccessLog;
import com.example.sendmail.repository.AccessLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository accessLogRepository;

    /** 認証済みリクエスト内から操作者メールを自動取得して記録する */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String resourceType, Long resourceId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()))
                ? auth.getName() : null;
        save(action, resourceType, resourceId, email, null);
    }

    /** ログイン成功・失敗など認証イベントで明示的にメールを渡す */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String resourceType, Long resourceId, String staffEmail) {
        save(action, resourceType, resourceId, staffEmail, null);
    }

    @Transactional(readOnly = true)
    public Page<AccessLog> findAll(Pageable pageable) {
        return accessLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    private void save(String action, String resourceType, Long resourceId, String staffEmail, String details) {
        AccessLog entry = new AccessLog();
        entry.setAction(action);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setStaffEmail(staffEmail);
        entry.setDetails(details);
        entry.setIpAddress(extractIp());
        accessLogRepository.save(entry);
    }

    private String extractIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) return null;
        HttpServletRequest req = servletAttrs.getRequest();
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
