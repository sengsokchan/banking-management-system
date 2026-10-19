package com.portfolio.banking.repo;

import com.portfolio.banking.domain.Account;
import com.portfolio.banking.domain.Enums;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select a from Account a
            join a.customer c
            where (:customerId is null or c.id = :customerId)
              and (:status is null or a.status = :status)
              and (
                    :query is null
                    or lower(a.accountNumber) like :query escape '\\'
                    or lower(c.customerNumber) like :query escape '\\'
                    or lower(c.firstName) like :query escape '\\'
                    or lower(c.lastName) like :query escape '\\'
                    or lower(concat(c.firstName, ' ', c.lastName)) like :query escape '\\'
              )
            """)
    Page<Account> search(
            @Param("customerId") UUID customerId,
            @Param("status") Enums.AccountStatus status,
            @Param("query") String query,
            Pageable pageable
    );

    long countByCustomerIdAndStatusNot(UUID customerId, Enums.AccountStatus status);

    long countByStatus(Enums.AccountStatus status);

    @Query("select coalesce(sum(a.balance), 0) from Account a where a.status <> :closed")
    BigDecimal sumBalanceExcluding(@Param("closed") Enums.AccountStatus closed);
}
