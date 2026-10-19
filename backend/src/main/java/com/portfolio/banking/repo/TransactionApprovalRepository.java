package com.portfolio.banking.repo;

import com.portfolio.banking.domain.TransactionApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionApprovalRepository extends JpaRepository<TransactionApproval, UUID> {

    Optional<TransactionApproval> findByTransactionId(UUID transactionId);
}
