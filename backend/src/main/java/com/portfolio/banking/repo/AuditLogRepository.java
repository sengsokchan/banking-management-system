package com.portfolio.banking.repo;

import com.portfolio.banking.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    @Query("""
            select a from AuditLog a
            where (:success is null or a.success = :success)
              and (
                    :query is null
                    or lower(a.action) like :query escape '\\'
                    or lower(a.actorUsername) like :query escape '\\'
                    or lower(a.entityType) like :query escape '\\'
                    or lower(a.message) like :query escape '\\'
              )
            """)
    Page<AuditLog> search(
            @Param("success") Boolean success,
            @Param("query") String query,
            Pageable pageable
    );
}
