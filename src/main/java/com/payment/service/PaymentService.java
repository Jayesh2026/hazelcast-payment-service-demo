package com.payment.service;

import java.util.List;

import com.payment.dto.PaymentRequest;
import com.payment.dto.PaymentResponse;
import com.payment.dto.StatusUpdateRequest;

public interface PaymentService {
    
    /**
     * Creates a new payment.
     *
     * @param request payment request
     * @return created payment response
     */
    PaymentResponse createPayment(PaymentRequest request);

    /**
     * Retrieves a payment by transaction ID.
     *
     * @param txnId transaction ID
     * @return payment response
     */
    PaymentResponse getPayment(String txnId);

    /**
     * Updates payment status.
     *
     * @param txnId transaction ID
     * @param request status update request
     * @return updated payment response
     */
    PaymentResponse updateStatus(String txnId, StatusUpdateRequest request);

    /**
     * Server-side predicate search over the payment-cache IMap
     * (PDF Chapter 4.3 — Predicates / Indexes).
     *
     * Filtering happens on the Hazelcast partition owners — only matching
     * entries are sent back to this member, not the whole map.
     *
     * @param status     optional exact-match filter (uses HASH index on "status")
     * @param merchantId optional exact-match filter (uses HASH index on "merchantId")
     * @param minAmount  optional inclusive lower bound on "amount"
     * @return matching payments currently present in the cache
     */
    List<PaymentResponse> searchPayments(String status, String merchantId, java.math.BigDecimal minAmount);
}

