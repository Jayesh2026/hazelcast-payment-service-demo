package com.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Item placed on the "payment-retry-queue" IQueue (PDF Chapter 5.1 — IQueue).
 *
 * Producer: PaymentRetryService.scheduleRetry()
 * Consumer: PaymentRetryService.processRetries() (runs on a @Scheduled tick)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedPayment implements Serializable {

    private String txnId;
    private String reason;       // why it failed, e.g. "GATEWAY_TIMEOUT"
    private int attemptNumber;
    private Instant scheduledAt; // earliest time this should be retried
}
