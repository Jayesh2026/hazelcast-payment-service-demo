package com.payment.exception;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String txnId) {
        super("Payment not found for txnId: " + txnId);
    }
}
