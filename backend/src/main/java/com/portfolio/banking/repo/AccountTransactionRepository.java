package com.portfolio.banking.repo;

import com.portfolio.banking.domain.AccountTransaction;
import com.portfolio.banking.domain.Enums;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, UUID> {

    Optional<AccountTransaction> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(Enums.TransactionStatus status);

    @Query("""
            select t from AccountTransaction t
            where (:status is null or t.status = :status)
              and (:type is null or t.transactionType = :type)
              and (
                    :accountId is null
                    or t.sourceAccount.id = :accountId
                    or t.destinationAccount.id = :accountId
              )
            """)
    Page<AccountTransaction> search(
            @Param("status") Enums.TransactionStatus status,
            @Param("type") Enums.TransactionType type,
            @Param("accountId") UUID accountId,
            Pageable pageable
    );
}
