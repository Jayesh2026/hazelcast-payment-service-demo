package com.payment.config;

/**
 * Shared Hazelcast structure names — 
 * (@Profile("embedded")) 
 * and referenced throughout PaymentServiceImpl, PaymentRetryService, etc.
 *
 * Keeping these in one place means PaymentServiceImpl it just asks Hazelcast for "payment-cache" etc.
 */
public final class HazelcastMapNames {

    public static final String PAYMENT_CACHE     = "payment-cache";
    public static final String IDEMPOTENCY_MAP   = "idempotency-keys";
    public static final String PAYMENT_EVENTS    = "payment-events";
    public static final String RETRY_QUEUE       = "payment-retry-queue";
    public static final String PAYMENT_SEQ       = "payment-seq";

    private HazelcastMapNames() {
    }
}
