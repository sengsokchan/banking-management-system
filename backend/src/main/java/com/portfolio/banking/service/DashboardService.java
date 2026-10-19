package com.portfolio.banking.service;

import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.repo.AccountRepository;
import com.portfolio.banking.repo.AccountTransactionRepository;
import com.portfolio.banking.repo.AuditLogRepository;
import com.portfolio.banking.repo.CustomerRepository;
import com.portfolio.banking.security.CurrentActor;
import com.portfolio.banking.security.StaffPrincipal;
import com.portfolio.banking.web.dto.ApiDtos.AuditLogResponse;
import com.portfolio.banking.web.dto.ApiDtos.DashboardResponse;
import com.portfolio.banking.web.dto.ApiDtos.TransactionResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final SettingsService settingsService;

    public DashboardService(
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            AccountTransactionRepository transactionRepository,
            AuditLogRepository auditLogRepository,
            SettingsService settingsService
    ) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        StaffPrincipal actor = CurrentActor.require();
        boolean canAudit = has(actor, "AUDIT_READ");
        boolean canTransactions = has(actor, "TRANSACTION_READ");
        List<AuditLogResponse> recentAudit = canAudit
                ? auditLogRepository.search(null, null, PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(AuditLogResponse::from)
                .toList()
                : List.of();
        List<TransactionResponse> recentTransactions = canTransactions
                ? transactionRepository
                .search(null, null, null, PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(TransactionResponse::from)
                .toList()
                : List.of();
        return new DashboardResponse(
                customerRepository.count(),
                accountRepository.countByStatus(Enums.AccountStatus.ACTIVE),
                accountRepository.countByStatus(Enums.AccountStatus.FROZEN),
                accountRepository.sumBalanceExcluding(Enums.AccountStatus.CLOSED),
                transactionRepository.countByStatus(Enums.TransactionStatus.PENDING_APPROVAL),
                settingsService.approvalThreshold(),
                recentTransactions,
                recentAudit
        );
    }

    private boolean has(StaffPrincipal actor, String permission) {
        return actor.getAuthorities().stream().anyMatch(authority -> permission.equals(authority.getAuthority()));
    }
}
