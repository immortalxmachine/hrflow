package com.hrflow.repo;

import com.hrflow.domain.ImportError;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {

    List<ImportError> findByBatchIdOrderByRowNumberAsc(Long batchId);
}
