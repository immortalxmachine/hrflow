package com.hrflow.repo;

import com.hrflow.domain.Request;
import com.hrflow.domain.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {

    Page<Request> findByRequesterId(Long requesterId, Pageable pageable);

    List<Request> findByStatus(RequestStatus status);

    Page<Request> findByStatus(RequestStatus status, Pageable pageable);
}
