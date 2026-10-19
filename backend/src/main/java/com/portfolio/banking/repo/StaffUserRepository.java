package com.portfolio.banking.repo;

import com.portfolio.banking.domain.StaffUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface StaffUserRepository extends JpaRepository<StaffUser, UUID> {

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<StaffUser> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<StaffUser> findWithAccessById(UUID id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Query("""
            select count(distinct u) from StaffUser u
            join u.roles r
            where r.name = 'ADMIN' and u.active = true
            """)
    long countActiveAdmins();
}
