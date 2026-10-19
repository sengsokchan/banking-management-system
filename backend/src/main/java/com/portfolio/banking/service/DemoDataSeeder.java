package com.portfolio.banking.service;

import com.portfolio.banking.config.AppProperties;
import com.portfolio.banking.domain.Account;
import com.portfolio.banking.domain.AccountTransaction;
import com.portfolio.banking.domain.AuditLog;
import com.portfolio.banking.domain.Customer;
import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.domain.Role;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.domain.TransactionApproval;
import com.portfolio.banking.repo.AccountRepository;
import com.portfolio.banking.repo.AccountTransactionRepository;
import com.portfolio.banking.repo.AuditLogRepository;
import com.portfolio.banking.repo.CustomerRepository;
import com.portfolio.banking.repo.RoleRepository;
import com.portfolio.banking.repo.StaffUserRepository;
import com.portfolio.banking.repo.TransactionApprovalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

@Component
@Order
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final StaffUserRepository staffUserRepository;
    private final RoleRepository roleRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final TransactionApprovalRepository approvalRepository;
    private final AuditLogRepository auditLogRepository;
    private final SequenceService sequenceService;

    public DemoDataSeeder(
            AppProperties properties,
            PasswordEncoder passwordEncoder,
            StaffUserRepository staffUserRepository,
            RoleRepository roleRepository,
            CustomerRepository customerRepository,
            AccountRepository accountRepository,
            AccountTransactionRepository transactionRepository,
            TransactionApprovalRepository approvalRepository,
            AuditLogRepository auditLogRepository,
            SequenceService sequenceService
    ) {
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.staffUserRepository = staffUserRepository;
        this.roleRepository = roleRepository;
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.approvalRepository = approvalRepository;
        this.auditLogRepository = auditLogRepository;
        this.sequenceService = sequenceService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled()) {
            return;
        }
        if (staffUserRepository.count() == 0) {
            seedStaff();
        }
        if (customerRepository.count() == 0) {
            seedLedger();
            log.info("Loaded simulated customers, accounts, and transactions.");
        }
    }

    private void seedStaff() {
        createUser("admin", "Avery Quinn", "admin@example.com", properties.getSeed().getAdminPassword(), "ADMIN");
        createUser("manager", "Morgan Ellis", "manager@example.com", properties.getSeed().getManagerPassword(), "MANAGER");
        createUser("teller", "Casey Nguyen", "teller@example.com", properties.getSeed().getTellerPassword(), "TELLER");
        createUser("auditor", "Rowan Blake", "auditor@example.com", properties.getSeed().getAuditorPassword(), "AUDITOR");
        log.info("Loaded simulated employee accounts: admin, manager, teller, auditor.");
    }

    private void createUser(String username, String fullName, String email, String password, String roleName) {
        Support.checkPassword(password);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        StaffUser user = new StaffUser();
        user.setUsername(username);
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setActive(true);
        user.setRoles(Set.of(role));
        staffUserRepository.save(user);
    }

    private void seedLedger() {
        StaffUser teller = staffUserRepository.findByUsername("teller").orElseThrow();
        Instant now = Instant.now();

        Customer amara = customer(teller, "Amara", "Okonkwo", "amara.okonkwo@example.com", "555-0101", LocalDate.of(1991, 4, 12), "18 Lantern Street", "Harbor City", now.minus(Duration.ofDays(240)));
        Customer luis = customer(teller, "Luis", "Herrera", "luis.herrera@example.com", "555-0108", LocalDate.of(1988, 11, 2), "7 Market Walk", "Northbridge", now.minus(Duration.ofDays(180)));
        Customer priya = customer(teller, "Priya", "Nair", "priya.nair@example.com", "555-0144", LocalDate.of(1995, 6, 20), "42 Cedar Court", "Westford", now.minus(Duration.ofDays(90)));
        Customer jonah = customer(teller, "Jonah", "Berg", "jonah.berg@example.com", "555-0160", LocalDate.of(1984, 1, 9), "3 Station Road", "Harbor City", now.minus(Duration.ofDays(400)));
        Customer hana = customer(teller, "Hana", "Sato", "hana.sato@example.com", "555-0172", LocalDate.of(1993, 8, 30), "90 Orchard Lane", "Northbridge", now.minus(Duration.ofDays(60)));
        Customer elena = customer(teller, "Elena", "Varga", "elena.varga@example.com", "555-0190", LocalDate.of(1990, 12, 5), "15 River Annex", "Westford", now.minus(Duration.ofDays(20)));
        Customer malik = customer(teller, "Malik", "Rahman", "malik.rahman@example.com", "555-0119", LocalDate.of(1986, 3, 17), "8 Hill Terrace", "Harbor City", now.minus(Duration.ofDays(500)));
        malik.setStatus(Enums.CustomerStatus.INACTIVE);
        Customer sofia = customer(teller, "Sofia", "Almeida", "sofia.almeida@example.com", "555-0133", LocalDate.of(1997, 2, 14), "27 Glasshouse Way", "Northbridge", now.minus(Duration.ofDays(12)));

        Account amaraChecking = account(teller, amara, Enums.AccountType.CHECKING, "2400.00", Enums.AccountStatus.ACTIVE, now.minus(Duration.ofDays(230)), null);
        Account amaraSavings = account(teller, amara, Enums.AccountType.SAVINGS, "15000.00", Enums.AccountStatus.ACTIVE, now.minus(Duration.ofDays(200)), null);
        Account luisChecking = account(teller, luis, Enums.AccountType.CHECKING, "940.50", Enums.AccountStatus.ACTIVE, now.minus(Duration.ofDays(170)), null);
        Account luisSavings = account(teller, luis, Enums.AccountType.SAVINGS, "100.00", Enums.AccountStatus.FROZEN, now.minus(Duration.ofDays(150)), null);
        Account priyaChecking = account(teller, priya, Enums.AccountType.CHECKING, "320.00", Enums.AccountStatus.ACTIVE, now.minus(Duration.ofDays(80)), null);
        Account jonahChecking = account(teller, jonah, Enums.AccountType.CHECKING, "0.00", Enums.AccountStatus.CLOSED, now.minus(Duration.ofDays(390)), now.minus(Duration.ofDays(30)));
        Account hanaSavings = account(teller, hana, Enums.AccountType.SAVINGS, "8100.00", Enums.AccountStatus.ACTIVE, now.minus(Duration.ofDays(55)), null);
        Account elenaChecking = account(teller, elena, Enums.AccountType.CHECKING, "15.25", Enums.AccountStatus.ACTIVE, now.minus(Duration.ofDays(18)), null);
        Account malikChecking = account(teller, malik, Enums.AccountType.CHECKING, "0.00", Enums.AccountStatus.CLOSED, now.minus(Duration.ofDays(490)), now.minus(Duration.ofDays(70)));
        Account sofiaChecking = account(teller, sofia, Enums.AccountType.CHECKING, "5000.00", Enums.AccountStatus.ACTIVE, now.minus(Duration.ofDays(10)), null);

        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "2500.00", null, amaraChecking, "Opening deposit", now.minus(Duration.ofDays(230)), now.minus(Duration.ofDays(230)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "15000.00", null, amaraSavings, "Opening deposit", now.minus(Duration.ofDays(200)), now.minus(Duration.ofDays(200)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "840.50", null, luisChecking, "Opening deposit", now.minus(Duration.ofDays(170)), now.minus(Duration.ofDays(170)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "100.00", null, luisSavings, "Opening deposit", now.minus(Duration.ofDays(150)), now.minus(Duration.ofDays(150)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "320.00", null, priyaChecking, "Opening deposit", now.minus(Duration.ofDays(80)), now.minus(Duration.ofDays(80)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "25.00", null, jonahChecking, "Opening deposit", now.minus(Duration.ofDays(390)), now.minus(Duration.ofDays(390)));
        txn(teller, Enums.TransactionType.WITHDRAWAL, Enums.TransactionStatus.COMPLETED, "25.00", jonahChecking, null, "Close-out withdrawal", now.minus(Duration.ofDays(31)), now.minus(Duration.ofDays(31)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "8100.00", null, hanaSavings, "Opening deposit", now.minus(Duration.ofDays(55)), now.minus(Duration.ofDays(55)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "15.25", null, elenaChecking, "Opening deposit", now.minus(Duration.ofDays(18)), now.minus(Duration.ofDays(18)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "40.00", null, malikChecking, "Opening deposit", now.minus(Duration.ofDays(490)), now.minus(Duration.ofDays(490)));
        txn(teller, Enums.TransactionType.WITHDRAWAL, Enums.TransactionStatus.COMPLETED, "40.00", malikChecking, null, "Close-out withdrawal", now.minus(Duration.ofDays(71)), now.minus(Duration.ofDays(71)));
        txn(teller, Enums.TransactionType.DEPOSIT, Enums.TransactionStatus.COMPLETED, "5000.00", null, sofiaChecking, "Opening deposit", now.minus(Duration.ofDays(10)), now.minus(Duration.ofDays(10)));
        txn(teller, Enums.TransactionType.TRANSFER, Enums.TransactionStatus.COMPLETED, "100.00", amaraChecking, luisChecking, "Simulated rent share", now.minus(Duration.ofDays(6)), now.minus(Duration.ofDays(6)));

        AccountTransaction pending = txn(teller, Enums.TransactionType.TRANSFER, Enums.TransactionStatus.PENDING_APPROVAL, "12000.00", amaraSavings, luisChecking, "Simulated property transfer", now.minus(Duration.ofHours(5)), null);
        TransactionApproval approval = new TransactionApproval();
        approval.setTransaction(pending);
        approval.setStatus(Enums.ApprovalStatus.PENDING);
        approval.setRequestedBy(teller);
        approvalRepository.save(approval);

        audit(teller, "DATA_SEEDED", "SYSTEM", null, "Loaded the simulated demonstration ledger.");
        audit(teller, "ACCOUNT_FROZEN", "ACCOUNT", luisSavings.getId().toString(), "Froze account " + luisSavings.getAccountNumber() + ".");
        audit(teller, "TRANSFER_SUBMITTED", "TRANSACTION", pending.getId().toString(), "Submitted transfer " + pending.getReferenceNumber() + " for approval.");
    }

    private Customer customer(StaffUser teller, String first, String last, String email, String phone, LocalDate dob, String address, String city, Instant registeredAt) {
        Customer customer = new Customer();
        customer.setCustomerNumber(sequenceService.nextCustomerNumber());
        customer.setFirstName(first);
        customer.setLastName(last);
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setDateOfBirth(dob);
        customer.setAddressLine(address);
        customer.setCity(city);
        customer.setCountry("Exampleland");
        customer.setStatus(Enums.CustomerStatus.ACTIVE);
        customer.setRegisteredAt(registeredAt);
        customer.setCreatedBy(teller);
        return customerRepository.save(customer);
    }

    private Account account(StaffUser teller, Customer customer, Enums.AccountType type, String balance, Enums.AccountStatus status, Instant openedAt, Instant closedAt) {
        Account account = new Account();
        account.setAccountNumber(sequenceService.nextAccountNumber());
        account.setCustomer(customer);
        account.setAccountType(type);
        account.setBalance(new BigDecimal(balance));
        account.setStatus(status);
        account.setOpenedAt(openedAt);
        account.setClosedAt(closedAt);
        account.setCreatedBy(teller);
        return accountRepository.save(account);
    }

    private AccountTransaction txn(
            StaffUser teller,
            Enums.TransactionType type,
            Enums.TransactionStatus status,
            String amount,
            Account source,
            Account destination,
            String description,
            Instant createdAt,
            Instant decidedAt
    ) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setReferenceNumber(sequenceService.nextTransactionReference());
        transaction.setTransactionType(type);
        transaction.setStatus(status);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setSourceAccount(source);
        transaction.setDestinationAccount(destination);
        transaction.setDescription(description);
        transaction.setCreatedBy(teller);
        transaction.setCreatedAt(createdAt);
        transaction.setDecidedAt(decidedAt);
        return transactionRepository.save(transaction);
    }

    private void audit(StaffUser actor, String action, String entityType, String entityId, String message) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setActorUsername(actor.getUsername());
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setSuccess(true);
        log.setMessage(message);
        auditLogRepository.save(log);
    }
}
