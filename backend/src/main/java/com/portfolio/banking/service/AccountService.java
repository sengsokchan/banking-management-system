package com.portfolio.banking.service;

import com.portfolio.banking.domain.Account;
import com.portfolio.banking.domain.Customer;
import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.repo.AccountRepository;
import com.portfolio.banking.repo.CustomerRepository;
import com.portfolio.banking.repo.StaffUserRepository;
import com.portfolio.banking.security.CurrentActor;
import com.portfolio.banking.security.StaffPrincipal;
import com.portfolio.banking.web.ApiException;
import com.portfolio.banking.web.PageResponse;
import com.portfolio.banking.web.dto.ApiDtos.AccountRequest;
import com.portfolio.banking.web.dto.ApiDtos.AccountResponse;
import com.portfolio.banking.web.dto.ApiDtos.MoneyMovementRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final StaffUserRepository staffUserRepository;
    private final SequenceService sequenceService;
    private final TransactionService transactionService;
    private final AuditService auditService;

    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository,
            StaffUserRepository staffUserRepository,
            SequenceService sequenceService,
            TransactionService transactionService,
            AuditService auditService
    ) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.staffUserRepository = staffUserRepository;
        this.sequenceService = sequenceService;
        this.transactionService = transactionService;
        this.auditService = auditService;
    }

    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> search(UUID customerId, Enums.AccountStatus status, String query, int page, int size) {
        return PageResponse.from(accountRepository.search(
                customerId,
                status,
                Support.contains(query),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt"))
        ).map(AccountResponse::from));
    }

    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        return AccountResponse.from(load(id));
    }

    @PreAuthorize("hasAuthority('ACCOUNT_WRITE')")
    @Transactional
    public AccountResponse create(AccountRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> ApiException.notFound("Customer was not found."));
        if (customer.getStatus() != Enums.CustomerStatus.ACTIVE) {
            auditService.failure("ACCOUNT_CREATE_FAILED", "CUSTOMER", customer.getId().toString(), "The customer is inactive.");
            throw ApiException.unprocessable("CUSTOMER_NOT_ACTIVE", "Accounts can only be opened for an active customer.");
        }
        BigDecimal opening = request.initialDeposit() == null ? Support.ZERO : Support.money(request.initialDeposit());
        if (opening.compareTo(Support.ZERO) > 0 && !canDeposit()) {
            throw ApiException.forbidden("An opening deposit requires permission to simulate deposits.");
        }
        Account account = new Account();
        account.setAccountNumber(sequenceService.nextAccountNumber());
        account.setCustomer(customer);
        account.setAccountType(request.accountType());
        account.setBalance(Support.ZERO);
        account.setStatus(Enums.AccountStatus.ACTIVE);
        account.setCreatedBy(staffUserRepository.getReferenceById(CurrentActor.require().getId()));
        accountRepository.saveAndFlush(account);
        if (opening.compareTo(Support.ZERO) > 0) {
            transactionService.deposit(new MoneyMovementRequest(account.getId(), opening, "Opening deposit", null), null);
        }
        auditService.success("ACCOUNT_CREATED", "ACCOUNT", account.getId().toString(), "Opened account " + account.getAccountNumber() + ".");
        return AccountResponse.from(account);
    }

    @PreAuthorize("hasAuthority('ACCOUNT_STATUS')")
    @Transactional
    public AccountResponse freeze(UUID id) {
        Account account = load(id);
        if (account.getStatus() != Enums.AccountStatus.ACTIVE) {
            auditService.failure("ACCOUNT_FREEZE_FAILED", "ACCOUNT", id.toString(), "Only an active account can be frozen.");
            throw ApiException.unprocessable("INVALID_STATUS_TRANSITION", "Only an active account can be frozen.");
        }
        account.setStatus(Enums.AccountStatus.FROZEN);
        auditService.success("ACCOUNT_FROZEN", "ACCOUNT", id.toString(), "Froze account " + account.getAccountNumber() + ".");
        return AccountResponse.from(account);
    }

    @PreAuthorize("hasAuthority('ACCOUNT_STATUS')")
    @Transactional
    public AccountResponse activate(UUID id) {
        Account account = load(id);
        if (account.getStatus() != Enums.AccountStatus.FROZEN) {
            auditService.failure("ACCOUNT_ACTIVATE_FAILED", "ACCOUNT", id.toString(), "Only a frozen account can be activated.");
            throw ApiException.unprocessable("INVALID_STATUS_TRANSITION", "Only a frozen account can be activated. Closed accounts stay closed.");
        }
        account.setStatus(Enums.AccountStatus.ACTIVE);
        auditService.success("ACCOUNT_ACTIVATED", "ACCOUNT", id.toString(), "Activated account " + account.getAccountNumber() + ".");
        return AccountResponse.from(account);
    }

    @PreAuthorize("hasAuthority('ACCOUNT_STATUS')")
    @Transactional
    public AccountResponse close(UUID id) {
        Account account = load(id);
        if (account.getStatus() == Enums.AccountStatus.CLOSED) {
            return AccountResponse.from(account);
        }
        if (account.getBalance().compareTo(Support.ZERO) != 0) {
            auditService.failure("ACCOUNT_CLOSE_FAILED", "ACCOUNT", id.toString(), "The account balance is not zero.");
            throw ApiException.unprocessable("ACCOUNT_NOT_ZERO", "Move the simulated balance to zero before closing this account.");
        }
        account.setStatus(Enums.AccountStatus.CLOSED);
        account.setClosedAt(Instant.now());
        auditService.success("ACCOUNT_CLOSED", "ACCOUNT", id.toString(), "Closed account " + account.getAccountNumber() + ".");
        return AccountResponse.from(account);
    }

    private Account load(UUID id) {
        return accountRepository.findById(id).orElseThrow(() -> ApiException.notFound("Account was not found."));
    }

    private boolean canDeposit() {
        StaffPrincipal principal = CurrentActor.require();
        return principal.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("TRANSACTION_DEPOSIT"));
    }
}
