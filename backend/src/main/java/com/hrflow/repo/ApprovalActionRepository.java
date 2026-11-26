package com.hrflow.repo;

import com.hrflow.domain.ApprovalAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, Long> {

    List<ApprovalAction> findByRequestIdOrderByActedAtAsc(Long requestId);
}
