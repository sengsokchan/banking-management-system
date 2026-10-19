package com.portfolio.banking.domain;

public final class Enums {

    private Enums() {
    }

    public enum RoleName {
        ADMIN, MANAGER, TELLER, AUDITOR
    }

    public enum CustomerStatus {
        ACTIVE, INACTIVE
    }

    public enum AccountType {
        SAVINGS, CHECKING
    }

    public enum AccountStatus {
        ACTIVE, FROZEN, CLOSED
    }

    public enum TransactionType {
        DEPOSIT, WITHDRAWAL, TRANSFER
    }

    public enum TransactionStatus {
        COMPLETED, PENDING_APPROVAL, REJECTED
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}
