package com.payment.controller;

import com.payment.service.Impl.PaymentRetryService;
import com.payment.service.Impl.PaymentReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Demo endpoints for Hazelcast features that don't fit naturally into the
 * main payment CRUD flow, but are covered in the PDF:
 *
 *  - IQueue retry pattern   (PDF Chapter 5.1)
 *  - CP Subsystem AtomicLong (PDF Chapter 5.5 / 9.x — payment reference numbers)
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class HazelcastDemoController {

    private final PaymentRetryService paymentRetryService;
    private final PaymentReferenceService paymentReferenceService;

    /**
     * Simulates a failed payment being scheduled for retry.
     *
     * Example:
     *   POST /api/demo/retry?txnId=TXN999&reason=GATEWAY_TIMEOUT
     *
     * Watch the logs ~10s later: PaymentRetryService.processRetries()
     * (runs every 5s on every member) will pick it up and log "RETRYING...".
     * If you run 2 instances, watch which one logs the retry — only ONE
     * member's poll/drain will consume any given item (IQueue is a single
     * shared distributed queue, not partitioned like IMap).
     */
    @PostMapping("/retry")
    public ResponseEntity<Map<String, Object>> scheduleRetry(
            @RequestParam String txnId,
            @RequestParam(defaultValue = "GATEWAY_TIMEOUT") String reason) {

        boolean enqueued = paymentRetryService.scheduleRetry(txnId, reason, 1);
        return ResponseEntity.ok(Map.of(
                "txnId", txnId,
                "reason", reason,
                "enqueued", enqueued,
                "note", "Will be retried in ~10s by whichever cluster member's scheduler drains it first"
        ));
    }

    /**
     * Generates the next payment reference number using a CP Subsystem
     * AtomicLong (PDF Chapter 5.5 — FencedLock/AtomicLong, RAFT-backed,
     * strongly consistent even during split-brain).
     *
     * Example:
     *   GET /api/demo/payment-ref
     *
     * Call this from both cluster members repeatedly — the sequence
     * numbers will NEVER repeat or collide, because the CP Subsystem
     * uses RAFT consensus across the cluster.
     */
    @GetMapping("/payment-ref")
    public ResponseEntity<Map<String, String>> generatePaymentRef() {
        return ResponseEntity.ok(Map.of("paymentRef", paymentReferenceService.generatePaymentRef()));
    }
}
