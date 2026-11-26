package com.hrflow.domain;

/** State of an outbound webhook delivery attempt. */
public enum DeliveryStatus {
    PENDING,  // created, not yet delivered successfully
    SUCCESS,  // target returned 2xx
    FAILED    // exhausted all retry attempts
}
