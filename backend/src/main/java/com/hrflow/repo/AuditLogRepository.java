package com.hrflow.repo;

import com.hrflow.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Paginated, filterable search. Each filter is optional: when a parameter is
     * null that predicate is skipped, so the same query serves "all" and any
     * combination of filters.
     */
    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:actorId IS NULL OR a.actorId = :actorId)
            """)
    Page<AuditLog> search(@Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("actorId") Long actorId,
                          Pageable pageable);
}
