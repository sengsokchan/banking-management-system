package com.portfolio.banking.service;

import com.portfolio.banking.domain.SystemSetting;
import com.portfolio.banking.repo.SystemSettingRepository;
import com.portfolio.banking.security.CurrentActor;
import com.portfolio.banking.web.ApiException;
import com.portfolio.banking.web.dto.ApiDtos.SettingsResponse;
import com.portfolio.banking.web.dto.ApiDtos.SettingsUpdateRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class SettingsService {

    static final String THRESHOLD_KEY = "approval.threshold";
    static final String BANK_NAME_KEY = "bank.display_name";

    private final SystemSettingRepository systemSettingRepository;
    private final AuditService auditService;

    public SettingsService(SystemSettingRepository systemSettingRepository, AuditService auditService) {
        this.systemSettingRepository = systemSettingRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        SystemSetting name = require(BANK_NAME_KEY);
        return new SettingsResponse(name.getSettingValue(), approvalThreshold(), name.getUpdatedAt());
    }

    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Transactional
    public SettingsResponse update(SettingsUpdateRequest request) {
        BigDecimal threshold = Support.positiveMoney(request.approvalThreshold());
        String name = request.bankDisplayName().trim();
        String actorId = CurrentActor.require().getId().toString();
        Instant now = Instant.now();
        SystemSetting thresholdSetting = require(THRESHOLD_KEY);
        thresholdSetting.setSettingValue(threshold.toPlainString());
        thresholdSetting.setUpdatedAt(now);
        thresholdSetting.setUpdatedBy(actorId);
        SystemSetting nameSetting = require(BANK_NAME_KEY);
        nameSetting.setSettingValue(name);
        nameSetting.setUpdatedAt(now);
        nameSetting.setUpdatedBy(actorId);
        auditService.success("SETTING_UPDATED", "SETTING", THRESHOLD_KEY, "Updated approval threshold and display name.");
        return new SettingsResponse(name, threshold, now);
    }

    @Transactional(readOnly = true)
    public BigDecimal approvalThreshold() {
        return Support.money(new BigDecimal(require(THRESHOLD_KEY).getSettingValue()));
    }

    private SystemSetting require(String key) {
        return systemSettingRepository.findById(key)
                .orElseThrow(() -> ApiException.notFound("Setting was not found."));
    }
}
