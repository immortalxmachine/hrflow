package com.hrflow.repo;

import com.hrflow.domain.RequestType;
import com.hrflow.domain.WorkflowDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

    /** The single active workflow for a request type (used when a request is raised). */
    Optional<WorkflowDefinition> findByRequestTypeAndActiveTrue(RequestType requestType);

    List<WorkflowDefinition> findByActiveTrue();
}
