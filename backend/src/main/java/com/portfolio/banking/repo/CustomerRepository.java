package com.portfolio.banking.repo;

import com.portfolio.banking.domain.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Query("""
            select c from Customer c
            where (
                :query is null
                or lower(c.customerNumber) like :query escape '\\'
                or lower(c.firstName) like :query escape '\\'
                or lower(c.lastName) like :query escape '\\'
                or lower(concat(c.firstName, ' ', c.lastName)) like :query escape '\\'
            )
            """)
    Page<Customer> search(@Param("query") String query, Pageable pageable);
}
