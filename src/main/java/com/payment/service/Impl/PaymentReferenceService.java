package com.payment.service.Impl;

import com.hazelcast.cp.IAtomicLong;
import com.hazelcast.core.HazelcastInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Demonstrates the CP Subsystem AtomicLong (PDF Chapter 5.5).
 *
 * Unlike the default IAtomicLong (which is AP — eventually consistent and
 * can diverge during split-brain), hazelcastInstance.getCPSubsystem()
 * .getAtomicLong(name) is backed by RAFT consensus: every increment is
 * agreed upon by a majority of CP members before it is confirmed. This
 * guarantees the sequence never produces duplicate or skipped numbers,
 * even if the cluster temporarily splits.
 *
 * Use case: generating unique, gap-free payment reference numbers.
 */
@Service
@RequiredArgsConstructor
public class PaymentReferenceService {

    private static final String PAYMENT_SEQ = "payment-seq";

    private final HazelcastInstance hazelcastInstance;

    /**
     * Generates a payment reference like: PAY-20260614-0000000001
     *
     * incrementAndGet() is atomic across the ENTIRE cluster — call this
     * from multiple members concurrently and every caller gets a unique,
     * strictly increasing number.
     */
    public String generatePaymentRef() {
        IAtomicLong paymentSeq = hazelcastInstance.getCPSubsystem().getAtomicLong(PAYMENT_SEQ);
        long seq = paymentSeq.incrementAndGet();

        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("PAY-%s-%010d", datePart, seq);
    }
}
