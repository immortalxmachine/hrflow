package com.hrflow.repo;

import com.hrflow.domain.Country;
import com.hrflow.domain.LeavePolicy;
import com.hrflow.domain.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {

    Optional<LeavePolicy> findByCountryAndLeaveType(Country country, LeaveType leaveType);
}
