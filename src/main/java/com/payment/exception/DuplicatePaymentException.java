package com.payment.exception;

/**
 * Thrown when the same idempotency key is used while the original
 * request is still being processed by this or another cluster member.
 * See PaymentService.createPayment() — uses IMap.putIfAbsent() (Chapter 9.1 of the guide).
 */
public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(String idempotencyKey) {
        super("Duplicate request detected for idempotencyKey: " + idempotencyKey
                + " — original request is still processing or already completed.");
    }
}
