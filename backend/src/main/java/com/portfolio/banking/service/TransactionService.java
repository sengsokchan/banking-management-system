package com.portfolio.banking.service;

import com.portfolio.banking.domain.Account;
import com.portfolio.banking.domain.AccountTransaction;
import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.domain.TransactionApproval;
import com.portfolio.banking.repo.AccountRepository;
import com.portfolio.banking.repo.AccountTransactionRepository;
import com.portfolio.banking.repo.StaffUserRepository;
import com.portfolio.banking.repo.TransactionApprovalRepository;
import com.portfolio.banking.security.CurrentActor;
import com.portfolio.banking.web.ApiException;
import com.portfolio.banking.web.PageResponse;
import com.portfolio.banking.web.dto.ApiDtos.MoneyMovementRequest;
import com.portfolio.banking.web.dto.ApiDtos.TransactionResponse;
import com.portfolio.banking.web.dto.ApiDtos.TransferRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final TransactionApprovalRepository approvalRepository;
    private final StaffUserRepository staffUserRepository;
    private final SequenceService sequenceService;
    private final AuditService auditService;
    private final SettingsService settingsService;
    private final TransactionService self;

    public TransactionService(
            AccountRepository accountRepository,
            AccountTransactionRepository transactionRepository,
            TransactionApprovalRepository approvalRepository,
            StaffUserRepository staffUserRepository,
            SequenceService sequenceService,
            AuditService auditService,
            SettingsService settingsService,
            @Lazy TransactionService self
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.approvalRepository = approvalRepository;
        this.staffUserRepository = staffUserRepository;
        this.sequenceService = sequenceService;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.self = self;
    }

    @PreAuthorize("hasAuthority('TRANSACTION_READ')")
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(Enums.TransactionStatus status, Enums.TransactionType type, UUID accountId, int page, int size) {
        return PageResponse.from(transactionRepository.search(
                status,
                type,
                accountId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(TransactionResponse::from));
    }

    @PreAuthorize("hasAuthority('TRANSACTION_READ')")
    @Transactional(readOnly = true)
    public TransactionResponse get(UUID id) {
        return TransactionResponse.from(load(id));
    }

    @PreAuthorize("hasAuthority('TRANSACTION_DEPOSIT')")
    public TransactionResponse deposit(MoneyMovementRequest request, String headerKey) {
        String key = Support.idempotencyKey(headerKey, request.idempotencyKey());
        BigDecimal amount = Support.positiveMoney(request.amount());
        String description = Support.blankToNull(request.description());
        String hash = Support.requestHash(Enums.TransactionType.DEPOSIT.name(), amount, null, request.accountId(), description);
        try {
            try {
                return self.depositInTransaction(request.accountId(), amount, description, key, hash);
            } catch (DataIntegrityViolationException ex) {
                return self.replay(key, hash);
            }
        } catch (ApiException ex) {
            auditService.failure("DEPOSIT_FAILED", "ACCOUNT", request.accountId().toString(), ex.getMessage());
            throw ex;
        }
    }

    @PreAuthorize("hasAuthority('TRANSACTION_WITHDRAW')")
    public TransactionResponse withdraw(MoneyMovementRequest request, String headerKey) {
        String key = Support.idempotencyKey(headerKey, request.idempotencyKey());
        BigDecimal amount = Support.positiveMoney(request.amount());
        String description = Support.blankToNull(request.description());
        String hash = Support.requestHash(Enums.TransactionType.WITHDRAWAL.name(), amount, request.accountId(), null, description);
        try {
            try {
                return self.withdrawInTransaction(request.accountId(), amount, description, key, hash);
            } catch (DataIntegrityViolationException ex) {
                return self.replay(key, hash);
            }
        } catch (ApiException ex) {
            auditService.failure("WITHDRAWAL_FAILED", "ACCOUNT", request.accountId().toString(), ex.getMessage());
            throw ex;
        }
    }

    @PreAuthorize("hasAuthority('TRANSACTION_TRANSFER')")
    public TransactionResponse transfer(TransferRequest request, String headerKey) {
        String key = Support.idempotencyKey(headerKey, request.idempotencyKey());
        BigDecimal amount = Support.positiveMoney(request.amount());
        String description = Support.blankToNull(request.description());
        String hash = Support.requestHash(
                Enums.TransactionType.TRANSFER.name(),
                amount,
                request.sourceAccountId(),
                request.destinationAccountId(),
                description
        );
        try {
            try {
                return self.transferInTransaction(
                        request.sourceAccountId(),
                        request.destinationAccountId(),
                        amount,
                        description,
                        key,
                        hash
                );
            } catch (DataIntegrityViolationException ex) {
                return self.replay(key, hash);
            }
        } catch (ApiException ex) {
            auditService.failure("TRANSFER_FAILED", "ACCOUNT", request.sourceAccountId().toString(), ex.getMessage());
            throw ex;
        }
    }

    @PreAuthorize("hasAuthority('TRANSACTION_APPROVE')")
    public TransactionResponse approve(UUID id, String comment) {
        try {
            return self.approveInTransaction(id, Support.blankToNull(comment));
        } catch (ApiException ex) {
            auditService.failure("TRANSACTION_APPROVAL_FAILED", "TRANSACTION", id.toString(), ex.getMessage());
            throw ex;
        }
    }

    @PreAuthorize("hasAuthority('TRANSACTION_APPROVE')")
    public TransactionResponse reject(UUID id, String comment) {
        try {
            return self.rejectInTransaction(id, Support.blankToNull(comment));
        } catch (ApiException ex) {
            auditService.failure("TRANSACTION_REJECTION_FAILED", "TRANSACTION", id.toString(), ex.getMessage());
            throw ex;
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse depositInTransaction(UUID accountId, BigDecimal amount, String description, String key, String hash) {
        TransactionResponse replay = replayIfPresent(key, hash);
        if (replay != null) {
            return replay;
        }
        Account account = lock(accountId);
        ensureMovable(account);
        account.setBalance(account.getBalance().add(amount));
        AccountTransaction transaction = saveCompleted(Enums.TransactionType.DEPOSIT, amount, null, account, description, key, hash);
        auditService.success("DEPOSIT_COMPLETED", "TRANSACTION", transaction.getId().toString(), "Deposited " + amount.toPlainString() + " to " + account.getAccountNumber() + ".");
        return TransactionResponse.from(transaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse withdrawInTransaction(UUID accountId, BigDecimal amount, String description, String key, String hash) {
        TransactionResponse replay = replayIfPresent(key, hash);
        if (replay != null) {
            return replay;
        }
        Account account = lock(accountId);
        ensureMovable(account);
        if (needsApproval(amount)) {
            ensureFunds(account, amount);
            AccountTransaction transaction = savePending(Enums.TransactionType.WITHDRAWAL, amount, account, null, description, key, hash);
            auditService.success("WITHDRAWAL_SUBMITTED", "TRANSACTION", transaction.getId().toString(), "Submitted withdrawal " + transaction.getReferenceNumber() + " for approval.");
            return TransactionResponse.from(transaction);
        }
        debit(account, amount);
        AccountTransaction transaction = saveCompleted(Enums.TransactionType.WITHDRAWAL, amount, account, null, description, key, hash);
        auditService.success("WITHDRAWAL_COMPLETED", "TRANSACTION", transaction.getId().toString(), "Withdrew " + amount.toPlainString() + " from " + account.getAccountNumber() + ".");
        return TransactionResponse.from(transaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse transferInTransaction(
            UUID sourceId,
            UUID destinationId,
            BigDecimal amount,
            String description,
            String key,
            String hash
    ) {
        TransactionResponse replay = replayIfPresent(key, hash);
        if (replay != null) {
            return replay;
        }
        if (sourceId.equals(destinationId)) {
            throw ApiException.unprocessable("SELF_TRANSFER", "A transfer must use two different accounts.");
        }
        // Lock in identifier order so two transfers cannot deadlock by locking opposite ends.
        Map<UUID, Account> locked = lockAll(sourceId, destinationId);
        Account source = locked.get(sourceId);
        Account destination = locked.get(destinationId);
        ensureMovable(source);
        ensureMovable(destination);
        if (needsApproval(amount)) {
            ensureFunds(source, amount);
            AccountTransaction transaction = savePending(Enums.TransactionType.TRANSFER, amount, source, destination, description, key, hash);
            auditService.success("TRANSFER_SUBMITTED", "TRANSACTION", transaction.getId().toString(), "Submitted transfer " + transaction.getReferenceNumber() + " for approval.");
            return TransactionResponse.from(transaction);
        }
        debit(source, amount);
        destination.setBalance(destination.getBalance().add(amount));
        AccountTransaction transaction = saveCompleted(Enums.TransactionType.TRANSFER, amount, source, destination, description, key, hash);
        auditService.success(
                "TRANSFER_COMPLETED",
                "TRANSACTION",
                transaction.getId().toString(),
                "Transferred " + amount.toPlainString() + " from " + source.getAccountNumber() + " to " + destination.getAccountNumber() + "."
        );
        return TransactionResponse.from(transaction);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse approveInTransaction(UUID id, String comment) {
        AccountTransaction transaction = load(id);
        TransactionApproval approval = requirePending(transaction);
        if (approval.getRequestedBy().getId().equals(CurrentActor.require().getId())) {
            throw ApiException.unprocessable("SELF_APPROVAL", "You cannot approve an operation you requested.");
        }
        UUID sourceId = transaction.getSourceAccount() == null ? null : transaction.getSourceAccount().getId();
        UUID destinationId = transaction.getDestinationAccount() == null ? null : transaction.getDestinationAccount().getId();
        Map<UUID, Account> locked = lockAll(sourceId, destinationId);
        if (transaction.getTransactionType() == Enums.TransactionType.WITHDRAWAL) {
            Account source = locked.get(sourceId);
            ensureMovable(source);
            debit(source, transaction.getAmount());
        } else if (transaction.getTransactionType() == Enums.TransactionType.TRANSFER) {
            Account source = locked.get(sourceId);
            Account destination = locked.get(destinationId);
            ensureMovable(source);
            ensureMovable(destination);
            debit(source, transaction.getAmount());
            destination.setBalance(destination.getBalance().add(transaction.getAmount()));
        } else {
            throw ApiException.unprocessable("NOT_PENDING", "This operation cannot be approved.");
        }
        Instant now = Instant.now();
        transaction.setStatus(Enums.TransactionStatus.COMPLETED);
        transaction.setDecidedAt(now);
        approval.setStatus(Enums.ApprovalStatus.APPROVED);
        approval.setDecidedBy(actor());
        approval.setDecidedAt(now);
        approval.setDecisionComment(comment);
        auditService.success("TRANSACTION_APPROVED", "TRANSACTION", transaction.getId().toString(), "Approved " + transaction.getReferenceNumber() + ".");
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public TransactionResponse rejectInTransaction(UUID id, String comment) {
        AccountTransaction transaction = load(id);
        TransactionApproval approval = requirePending(transaction);
        if (approval.getRequestedBy().getId().equals(CurrentActor.require().getId())) {
            throw ApiException.unprocessable("SELF_APPROVAL", "You cannot reject an operation you requested.");
        }
        Instant now = Instant.now();
        transaction.setStatus(Enums.TransactionStatus.REJECTED);
        transaction.setDecidedAt(now);
        approval.setStatus(Enums.ApprovalStatus.REJECTED);
        approval.setDecidedBy(actor());
        approval.setDecidedAt(now);
        approval.setDecisionComment(comment);
        auditService.success("TRANSACTION_REJECTED", "TRANSACTION", transaction.getId().toString(), "Rejected " + transaction.getReferenceNumber() + ".");
        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse replay(String key, String hash) {
        if (key == null) {
            throw ApiException.conflict("DATA_CONFLICT", "The request conflicts with an existing record.");
        }
        AccountTransaction existing = transactionRepository.findByIdempotencyKey(key)
                .orElseThrow(() -> ApiException.conflict("DATA_CONFLICT", "The request conflicts with an existing record."));
        if (!hash.equals(existing.getRequestHash())) {
            throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "This idempotency key was already used for a different request.");
        }
        return TransactionResponse.from(existing);
    }

    private TransactionResponse replayIfPresent(String key, String hash) {
        if (key == null) {
            return null;
        }
        return transactionRepository.findByIdempotencyKey(key)
                .map(existing -> {
                    if (!hash.equals(existing.getRequestHash())) {
                        throw ApiException.conflict("IDEMPOTENCY_CONFLICT", "This idempotency key was already used for a different request.");
                    }
                    return TransactionResponse.from(existing);
                })
                .orElse(null);
    }

    private boolean needsApproval(BigDecimal amount) {
        return amount.compareTo(settingsService.approvalThreshold()) >= 0;
    }

    private void ensureMovable(Account account) {
        if (account.getStatus() != Enums.AccountStatus.ACTIVE) {
            throw ApiException.unprocessable("ACCOUNT_NOT_ACTIVE", "Only active accounts can send or receive simulated funds.");
        }
        if (account.getCustomer().getStatus() != Enums.CustomerStatus.ACTIVE) {
            throw ApiException.unprocessable("CUSTOMER_NOT_ACTIVE", "The account owner is inactive.");
        }
    }

    private void ensureFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw ApiException.unprocessable("INSUFFICIENT_FUNDS", "The account does not have enough simulated balance.");
        }
    }

    private void debit(Account account, BigDecimal amount) {
        ensureFunds(account, amount);
        account.setBalance(account.getBalance().subtract(amount));
    }

    private AccountTransaction saveCompleted(
            Enums.TransactionType type,
            BigDecimal amount,
            Account source,
            Account destination,
            String description,
            String key,
            String hash
    ) {
        AccountTransaction transaction = base(type, amount, source, destination, description, key, hash);
        transaction.setStatus(Enums.TransactionStatus.COMPLETED);
        return transactionRepository.save(transaction);
    }

    private AccountTransaction savePending(
            Enums.TransactionType type,
            BigDecimal amount,
            Account source,
            Account destination,
            String description,
            String key,
            String hash
    ) {
        AccountTransaction transaction = base(type, amount, source, destination, description, key, hash);
        transaction.setStatus(Enums.TransactionStatus.PENDING_APPROVAL);
        transactionRepository.save(transaction);
        TransactionApproval approval = new TransactionApproval();
        approval.setTransaction(transaction);
        approval.setStatus(Enums.ApprovalStatus.PENDING);
        approval.setRequestedBy(actor());
        approvalRepository.save(approval);
        transaction.setApproval(approval);
        return transaction;
    }

    private AccountTransaction base(
            Enums.TransactionType type,
            BigDecimal amount,
            Account source,
            Account destination,
            String description,
            String key,
            String hash
    ) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setReferenceNumber(sequenceService.nextTransactionReference());
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setSourceAccount(source);
        transaction.setDestinationAccount(destination);
        transaction.setDescription(description);
        transaction.setIdempotencyKey(key);
        transaction.setRequestHash(hash);
        transaction.setCreatedBy(actor());
        return transaction;
    }

    private TransactionApproval requirePending(AccountTransaction transaction) {
        TransactionApproval approval = transaction.getApproval();
        if (transaction.getStatus() != Enums.TransactionStatus.PENDING_APPROVAL || approval == null || approval.getStatus() != Enums.ApprovalStatus.PENDING) {
            throw ApiException.unprocessable("NOT_PENDING", "This operation is not waiting for approval.");
        }
        return approval;
    }

    private Map<UUID, Account> lockAll(UUID... ids) {
        Map<UUID, Account> locked = new LinkedHashMap<>();
        Arrays.stream(ids).filter(Objects::nonNull).distinct().sorted().forEach(id -> locked.put(id, lock(id)));
        return locked;
    }

    private Account lock(UUID id) {
        return accountRepository.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Account was not found."));
    }

    private AccountTransaction load(UUID id) {
        return transactionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Transaction was not found."));
    }

    private StaffUser actor() {
        return staffUserRepository.getReferenceById(CurrentActor.require().getId());
    }
}
