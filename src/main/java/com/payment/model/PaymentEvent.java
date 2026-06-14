package com.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Broadcast over the "payment-events" ITopic (PDF Chapter 5.2 — ITopic).
 * All cluster members subscribed to this topic receive every published event.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent implements Serializable {

    private String txnId;
    private String eventType; // CREATED, STATUS_UPDATED, SETTLED, FAILED, REFUNDED
    private String status;
    private BigDecimal amount;
    private Instant timestamp;
}
