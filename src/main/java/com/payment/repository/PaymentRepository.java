package com.payment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.payment.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByTxnId(String txnId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByTxnId(String txnId);
}
