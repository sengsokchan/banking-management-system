package com.portfolio.banking.service;

import com.portfolio.banking.domain.AuditLog;
import com.portfolio.banking.repo.AuditLogRepository;
import com.portfolio.banking.repo.StaffUserRepository;
import com.portfolio.banking.security.StaffPrincipal;
import com.portfolio.banking.web.RequestMetadata;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final StaffUserRepository staffUserRepository;

    public AuditService(AuditLogRepository auditLogRepository, StaffUserRepository staffUserRepository) {
        this.auditLogRepository = auditLogRepository;
        this.staffUserRepository = staffUserRepository;
    }

    @Transactional
    public void success(String action, String entityType, String entityId, String message) {
        StaffPrincipal actor = currentActor();
        write(actor == null ? null : actor.getId(), actor == null ? "system" : actor.getUsername(), action, entityType, entityId, true, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String action, String entityType, String entityId, String message) {
        StaffPrincipal actor = currentActor();
        write(actor == null ? null : actor.getId(), actor == null ? "anonymous" : actor.getUsername(), action, entityType, entityId, false, message);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failureAs(String username, String action, String entityType, String entityId, String message) {
        write(null, username == null || username.isBlank() ? "anonymous" : username, action, entityType, entityId, false, message);
    }

    @Transactional
    public void recordInCurrentTransaction(UUID actorId, String username, String action, String entityType, String entityId, boolean success, String message) {
        write(actorId, username, action, entityType, entityId, success, message);
    }

    private void write(UUID actorId, String username, String action, String entityType, String entityId, boolean success, String message) {
        AuditLog log = new AuditLog();
        if (actorId != null) {
            log.setActor(staffUserRepository.getReferenceById(actorId));
        }
        log.setActorUsername(trim(username, 50));
        log.setAction(trim(action, 80));
        log.setEntityType(trim(entityType, 50));
        log.setEntityId(entityId);
        log.setSuccess(success);
        log.setMessage(trim(message, 1000));
        log.setIpAddress(RequestMetadata.clientIp());
        auditLogRepository.save(log);
    }

    private StaffPrincipal currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof StaffPrincipal principal)) {
            return null;
        }
        return principal;
    }

    private String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
