package com.hrflow.domain;

/** Lifecycle state of a request as it moves through its workflow. */
public enum RequestStatus {
    PENDING,   // awaiting a decision on the current step
    APPROVED,  // all steps approved (terminal)
    REJECTED,  // rejected at some step (terminal)
    CANCELLED  // withdrawn by the requester (terminal)
}
