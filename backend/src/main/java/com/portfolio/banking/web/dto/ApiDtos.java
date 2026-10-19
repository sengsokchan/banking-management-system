package com.portfolio.banking.web.dto;

import com.portfolio.banking.domain.Account;
import com.portfolio.banking.domain.AccountTransaction;
import com.portfolio.banking.domain.AuditLog;
import com.portfolio.banking.domain.Customer;
import com.portfolio.banking.domain.Enums;
import com.portfolio.banking.domain.Role;
import com.portfolio.banking.domain.StaffUser;
import com.portfolio.banking.domain.TransactionApproval;
import com.portfolio.banking.security.StaffPrincipal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {

    private ApiDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 72) String password
    ) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    public record LogoutRequest(@Size(max = 200) String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(max = 72) String newPassword
    ) {
    }

    public record UserProfile(
            UUID id,
            String username,
            String fullName,
            String email,
            List<String> roles,
            List<String> permissions
    ) {
        public static UserProfile from(StaffPrincipal principal) {
            List<String> roles = principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring(5))
                    .sorted()
                    .toList();
            List<String> permissions = principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .filter(authority -> !authority.startsWith("ROLE_"))
                    .sorted()
                    .toList();
            return new UserProfile(principal.getId(), principal.getUsername(), principal.getFullName(), principal.getEmail(), roles, permissions);
        }
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, UserProfile user) {
    }

    public record CustomerRequest(
            @NotBlank(message = "First name is required.") @Size(max = 80) String firstName,
            @NotBlank(message = "Last name is required.") @Size(max = 80) String lastName,
            @NotBlank(message = "Email is required.") @Email(message = "Email must be a valid address.") @Size(max = 160) String email,
            @Size(max = 30) String phone,
            @NotNull(message = "Date of birth is required.") @Past(message = "Date of birth must be in the past.") LocalDate dateOfBirth,
            @NotBlank(message = "Address is required.") @Size(max = 160) String addressLine,
            @NotBlank(message = "City is required.") @Size(max = 80) String city,
            @NotBlank(message = "Country is required.") @Size(max = 80) String country
    ) {
    }

    public record CustomerResponse(
            UUID id,
            String customerNumber,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            String addressLine,
            String city,
            String country,
            String status,
            Instant registeredAt
    ) {
        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(
                    customer.getId(),
                    customer.getCustomerNumber(),
                    customer.getFirstName(),
                    customer.getLastName(),
                    customer.getEmail(),
                    customer.getPhone(),
                    customer.getDateOfBirth(),
                    customer.getAddressLine(),
                    customer.getCity(),
                    customer.getCountry(),
                    customer.getStatus().name(),
                    customer.getRegisteredAt()
            );
        }
    }

    public record CustomerDetailResponse(CustomerResponse customer, List<AccountResponse> accounts) {
    }

    public record AccountRequest(
            @NotNull(message = "Customer is required.") UUID customerId,
            @NotNull(message = "Account type is required.") Enums.AccountType accountType,
            @DecimalMin(value = "0.00", message = "Opening deposit cannot be negative.")
            @Digits(integer = 17, fraction = 2, message = "Opening deposit uses at most two decimal places.")
            BigDecimal initialDeposit
    ) {
    }

    public record AccountResponse(
            UUID id,
            String accountNumber,
            UUID customerId,
            String customerNumber,
            String customerName,
            String accountType,
            BigDecimal balance,
            String currency,
            String status,
            Instant openedAt,
            Instant closedAt
    ) {
        public static AccountResponse from(Account account) {
            Customer customer = account.getCustomer();
            return new AccountResponse(
                    account.getId(),
                    account.getAccountNumber(),
                    customer.getId(),
                    customer.getCustomerNumber(),
                    customer.displayName(),
                    account.getAccountType().name(),
                    account.getBalance(),
                    account.getCurrency(),
                    account.getStatus().name(),
                    account.getOpenedAt(),
                    account.getClosedAt()
            );
        }
    }

    public record MoneyMovementRequest(
            @NotNull(message = "Account is required.") UUID accountId,
            @NotNull(message = "Amount is required.")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
            @Digits(integer = 17, fraction = 2, message = "Amount uses at most two decimal places.")
            BigDecimal amount,
            @Size(max = 255) String description,
            @Size(max = 80) String idempotencyKey
    ) {
    }

    public record TransferRequest(
            @NotNull(message = "Source account is required.") UUID sourceAccountId,
            @NotNull(message = "Destination account is required.") UUID destinationAccountId,
            @NotNull(message = "Amount is required.")
            @DecimalMin(value = "0.01", message = "Amount must be greater than zero.")
            @Digits(integer = 17, fraction = 2, message = "Amount uses at most two decimal places.")
            BigDecimal amount,
            @Size(max = 255) String description,
            @Size(max = 80) String idempotencyKey
    ) {
    }

    public record DecisionRequest(@Size(max = 500) String comment) {
    }

    public record TransactionResponse(
            UUID id,
            String referenceNumber,
            String type,
            String status,
            BigDecimal amount,
            String currency,
            UUID sourceAccountId,
            String sourceAccountNumber,
            UUID destinationAccountId,
            String destinationAccountNumber,
            String description,
            String createdBy,
            Instant createdAt,
            Instant decidedAt,
            String approvalStatus,
            String approvalComment
    ) {
        public static TransactionResponse from(AccountTransaction transaction) {
            Account source = transaction.getSourceAccount();
            Account destination = transaction.getDestinationAccount();
            TransactionApproval approval = transaction.getApproval();
            return new TransactionResponse(
                    transaction.getId(),
                    transaction.getReferenceNumber(),
                    transaction.getTransactionType().name(),
                    transaction.getStatus().name(),
                    transaction.getAmount(),
                    Account.CURRENCY,
                    source == null ? null : source.getId(),
                    source == null ? null : source.getAccountNumber(),
                    destination == null ? null : destination.getId(),
                    destination == null ? null : destination.getAccountNumber(),
                    transaction.getDescription(),
                    transaction.getCreatedBy() == null ? null : transaction.getCreatedBy().getUsername(),
                    transaction.getCreatedAt(),
                    transaction.getDecidedAt(),
                    approval == null ? null : approval.getStatus().name(),
                    approval == null ? null : approval.getDecisionComment()
            );
        }
    }

    public record AuditLogResponse(
            UUID id,
            String actorUsername,
            String action,
            String entityType,
            String entityId,
            boolean success,
            String message,
            String ipAddress,
            Instant createdAt
    ) {
        public static AuditLogResponse from(AuditLog log) {
            return new AuditLogResponse(
                    log.getId(),
                    log.getActorUsername(),
                    log.getAction(),
                    log.getEntityType(),
                    log.getEntityId(),
                    log.isSuccess(),
                    log.getMessage(),
                    log.getIpAddress(),
                    log.getCreatedAt()
            );
        }
    }

    public record UserResponse(
            UUID id,
            String username,
            String fullName,
            String email,
            boolean active,
            List<String> roles,
            Instant createdAt
    ) {
        public static UserResponse from(StaffUser user) {
            List<String> roles = user.getRoles().stream()
                    .map(Role::getName)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            return new UserResponse(user.getId(), user.getUsername(), user.getFullName(), user.getEmail(), user.isActive(), roles, user.getCreatedAt());
        }
    }

    public record UserWriteRequest(
            @NotBlank @Size(min = 3, max = 32) String username,
            @NotBlank @Size(max = 72) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotEmpty List<@NotBlank String> roles
    ) {
    }

    public record UserUpdateRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotNull Boolean active,
            @NotEmpty List<@NotBlank String> roles
    ) {
    }

    public record PasswordResetRequest(@NotBlank @Size(max = 72) String password) {
    }

    public record RoleResponse(String name, String description) {
        public static RoleResponse from(Role role) {
            return new RoleResponse(role.getName(), role.getDescription());
        }
    }

    public record SettingsResponse(String bankDisplayName, BigDecimal approvalThreshold, Instant updatedAt) {
    }

    public record SettingsUpdateRequest(
            @NotBlank @Size(max = 80) String bankDisplayName,
            @NotNull
            @DecimalMin(value = "0.01", message = "Approval threshold must be greater than zero.")
            @Digits(integer = 17, fraction = 2)
            BigDecimal approvalThreshold
    ) {
    }

    public record DashboardResponse(
            long customerCount,
            long activeAccounts,
            long frozenAccounts,
            BigDecimal simulatedBalances,
            long pendingApprovals,
            BigDecimal approvalThreshold,
            List<TransactionResponse> recentTransactions,
            List<AuditLogResponse> recentAudit
    ) {
    }
}
