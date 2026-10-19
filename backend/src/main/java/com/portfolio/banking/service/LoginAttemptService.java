package com.portfolio.banking.service;

import com.portfolio.banking.config.AppProperties;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.repo.StaffUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class LoginAttemptService {

    private final StaffUserRepository staffUserRepository;
    private final AuditService auditService;
    private final AppProperties appProperties;

    public LoginAttemptService(StaffUserRepository staffUserRepository, AuditService auditService, AppProperties appProperties) {
        this.staffUserRepository = staffUserRepository;
        this.auditService = auditService;
        this.appProperties = appProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UUID userId) {
        StaffUser user = staffUserRepository.findById(userId).orElseThrow();
        int attempts = user.getFailedAttemptCount() + 1;
        user.setFailedAttemptCount(attempts);
        if (attempts >= appProperties.getSecurity().getMaxFailedAttempts()) {
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(appProperties.getSecurity().getLockMinutes())));
        }
        auditService.recordInCurrentTransaction(
                user.getId(),
                user.getUsername(),
                "LOGIN_FAILURE",
                "USER",
                user.getId().toString(),
                false,
                "Sign-in failed."
        );
    }
}
