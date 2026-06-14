package com.payment.service.Impl;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.payment.model.FailedPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates IQueue — a distributed, FIFO, blocking queue (PDF Chapter 5.1).
 *
 * Producer side:  scheduleRetry() — called when a payment "fails" and needs
 *                 to be retried after a short delay.
 *
 * Consumer side:  processRetries() — runs every 5 seconds on EVERY cluster
 *                 member via @Scheduled. Because IQueue is a single shared
 *                 distributed queue (backed by one partition + backup, not
 *                 271 partitions like IMap), only ONE member's poll() call
 *                 will actually receive any given item — Hazelcast handles
 *                 the distribution. This is the producer-consumer pattern
 *                 from the PDF's payment-retry-queue example.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRetryService {

    private static final String RETRY_QUEUE = "payment-retry-queue";

    private final HazelcastInstance hazelcastInstance;

    private IQueue<FailedPayment> retryQueue() {
        return hazelcastInstance.getQueue(RETRY_QUEUE);
    }

    /**
     * Producer: enqueue a failed payment for retry after a short delay.
     *
     * offer() with a timeout — if the queue is somehow full/unavailable
     * within 5 seconds, we log an error instead of blocking forever.
     */
    public boolean scheduleRetry(String txnId, String reason, int attemptNumber) {
        FailedPayment retry = FailedPayment.builder()
                .txnId(txnId)
                .reason(reason)
                .attemptNumber(attemptNumber)
                .scheduledAt(Instant.now().plus(10, ChronoUnit.SECONDS)) // retry after 10s
                .build();

        try {
            boolean enqueued = retryQueue().offer(retry, 5, TimeUnit.SECONDS);
            if (enqueued) {
                log.info("Enqueued retry for txnId={}, attempt={}, reason={}",
                        txnId, attemptNumber, reason);
            } else {
                log.error("payment-retry-queue full! Could not enqueue txnId={}", txnId);
            }
            return enqueued;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while enqueuing retry for txnId={}", txnId, e);
            return false;
        }
    }

    /**
     * Consumer: drains due retries every 5 seconds.
     *
     * Uses drainTo() — atomically removes up to N elements in one call
     * (PDF Chapter 5.1: "drainTo — high-performance batch drain").
     *
     * Items whose scheduledAt time hasn't arrived yet are re-offered
     * back onto the queue (simple "not due yet" requeue strategy).
     */
    @Scheduled(fixedDelay = 5000)
    public void processRetries() {
        IQueue<FailedPayment> queue = retryQueue();
        if (queue.isEmpty()) {
            return;
        }

        List<FailedPayment> batch = new ArrayList<>();
        queue.drainTo(batch, 50); // drain up to 50 items in one shot

        for (FailedPayment item : batch) {
            if (Instant.now().isAfter(item.getScheduledAt())) {
                log.info("RETRYING payment txnId={}, attempt={}, reason={} (consumed by this member)",
                        item.getTxnId(), item.getAttemptNumber(), item.getReason());
                // In a real system: re-call the bank gateway / settlement logic here.
            } else {
                // Not due yet — put it back on the queue for a future tick
                retryQueue().offer(item);
            }
        }
    }
}
