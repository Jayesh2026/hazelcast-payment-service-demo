package com.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private String txnId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    // Helpful flag so the client knows if this came from cache or DB
    // (purely for learning/demo purposes — remove in real production responses)
    private boolean servedFromCache;
}
