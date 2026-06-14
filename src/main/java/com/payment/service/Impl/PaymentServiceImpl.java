package com.payment.service.Impl;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
import com.hazelcast.topic.ITopic;
import com.payment.config.HazelcastMapNames;
import com.payment.dto.PaymentRequest;
import com.payment.dto.PaymentResponse;
import com.payment.dto.StatusUpdateRequest;
import com.payment.exception.DuplicatePaymentException;
import com.payment.exception.PaymentNotFoundException;
import com.payment.model.Payment;
import com.payment.model.PaymentCacheDto;
import com.payment.model.PaymentEvent;
import com.payment.model.PaymentStatusUpdater;
import com.payment.repository.PaymentRepository;
import com.payment.service.PaymentService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core payment business logic.
 *
 * Demonstrates concepts from the Hazelcast Deep Dive PDF:
 *  - Chapter 4.3 (IMap put/get, lock/unlock, EntryProcessor)
 *  - Chapter 5.2 (ITopic pub/sub for payment events)
 *  - Chapter 9.1 (Idempotency key pattern via putIfAbsent)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private final PaymentRepository paymentRepository;
    private final HazelcastInstance hazelcastInstance;

    private IMap<String, PaymentCacheDto> paymentCache() {
        return hazelcastInstance.getMap(HazelcastMapNames.PAYMENT_CACHE);
    }

    private IMap<String, String> idempotencyMap() {
        return hazelcastInstance.getMap(HazelcastMapNames.IDEMPOTENCY_MAP);
    }

    private ITopic<PaymentEvent> paymentEventsTopic() {
        return hazelcastInstance.getTopic(PAYMENT_EVENTS_TOPIC);
    }

    /**
     * Create a new payment.
     *
     * Idempotency pattern (PDF Chapter 9.1):
     *  - IMap.putIfAbsent() atomically claims the idempotency key across the
     *    whole cluster. If another request already claimed it, we know this
     *    is a retry/duplicate and respond accordingly.
     *
     * Distributed lock (PDF Chapter 4.3):
     *  - We additionally lock on txnId while writing to the DB + cache, so
     *    two threads/members can never process the same txnId concurrently.
     */
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {

        IMap<String, String> idempotencyMap = idempotencyMap();
        String idempotencyKey = request.getTxnId(); // using txnId as the idempotency key

        // STEP 1: Atomically claim the idempotency key.
        // putIfAbsent returns null if THIS call claimed it, or the existing
        // value if someone else already claimed it.
        String existing = idempotencyMap.putIfAbsent(idempotencyKey, "PROCESSING", 24, TimeUnit.HOURS);
        if (existing != null) {
            throw new DuplicatePaymentException(idempotencyKey);
        }

        IMap<String, PaymentCacheDto> cache = paymentCache();

        // STEP 2: Distributed lock on txnId — prevents concurrent writers
        // even if they somehow bypass the idempotency map (defense in depth).
        cache.lock(request.getTxnId());
        try {
            if (paymentRepository.existsByTxnId(request.getTxnId())) {
                throw new DuplicatePaymentException(idempotencyKey);
            }

            Payment payment = Payment.builder()
                    .txnId(request.getTxnId())
                    .merchantId(request.getMerchantId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .status("PENDING")
                    .idempotencyKey(idempotencyKey)
                    .updatedBy("payment-service")
                    .createdAt(Instant.now())
                    .build();

            Payment saved = paymentRepository.save(payment);

            // STEP 3: Populate the cache (write-through)
            PaymentCacheDto dto = PaymentCacheDto.from(saved);
            cache.put(saved.getTxnId(), dto, 10, TimeUnit.MINUTES);

            // STEP 4: Mark idempotency key as completed (so retries return same result)
            idempotencyMap.put(idempotencyKey, "COMPLETED", 24, TimeUnit.HOURS);

            // STEP 5: Broadcast event to all cluster members (PDF Chapter 5.2)
            paymentEventsTopic().publish(PaymentEvent.builder()
                    .txnId(saved.getTxnId())
                    .eventType("CREATED")
                    .status(saved.getStatus())
                    .amount(saved.getAmount())
                    .timestamp(Instant.now())
                    .build());

            log.info("Payment created: txnId={}, amount={}, status={}",
                    saved.getTxnId(), saved.getAmount(), saved.getStatus());

            return toResponse(saved, false);

        } catch (DuplicatePaymentException e) {
            // Roll back the idempotency claim on failure so the client can retry
            idempotencyMap.remove(idempotencyKey);
            throw e;
        } finally {
            cache.unlock(request.getTxnId());
        }
    }

    /**
     * Get a payment by txnId.
     *
     * Cache-aside pattern: check Hazelcast IMap first (Near Cache makes
     * repeated reads on the same member extremely fast — PDF Chapter 9.2).
     * On miss, fall back to PostgreSQL and populate the cache.
     */
    public PaymentResponse getPayment(String txnId) {
        IMap<String, PaymentCacheDto> cache = paymentCache();

        PaymentCacheDto cached = cache.get(txnId);
        if (cached != null) {
            log.debug("Cache HIT for txnId={}", txnId);
            return toResponse(cached, true);
        }

        log.debug("Cache MISS for txnId={} — querying DB", txnId);
        Payment payment = paymentRepository.findByTxnId(txnId)
                .orElseThrow(() -> new PaymentNotFoundException(txnId));

        cache.put(txnId, PaymentCacheDto.from(payment), 10, TimeUnit.MINUTES);
        return toResponse(payment, false);
    }

    /**
     * Server-side predicate search (PDF Chapter 4.3 — Predicates / Indexes).
     *
     * Builds a combined Predicate (AND of whichever filters were supplied)
     * and runs it via IMap.values(predicate). Hazelcast evaluates this on
     * each partition owner using the HASH indexes configured on "status"
     * and "merchantId" in HazelcastConfig — only matching entries cross
     * the network back to this member.
     *
     * NOTE: this only searches entries CURRENTLY in the payment-cache IMap
     * (i.e. created/read within the last 10-minute TTL window). It is a
     * cache query, not a database query — that's intentional, to
     * demonstrate IMap predicates specifically.
     */
    @Override
    public List<PaymentResponse> searchPayments(String status, String merchantId, java.math.BigDecimal minAmount) {
        IMap<String, PaymentCacheDto> cache = paymentCache();

        List<Predicate<String, PaymentCacheDto>> predicates = new java.util.ArrayList<>();

        if (status != null && !status.isBlank()) {
            predicates.add(Predicates.equal("status", status));          // uses HASH index on "status"
        }
        if (merchantId != null && !merchantId.isBlank()) {
            predicates.add(Predicates.equal("merchantId", merchantId));  // uses HASH index on "merchantId"
        }
        if (minAmount != null) {
            predicates.add(Predicates.greaterEqual("amount", minAmount)); // range scan on "amount"
        }

        Collection<PaymentCacheDto> results;
        if (predicates.isEmpty()) {
            // No filters supplied — return everything currently cached
            results = cache.values();
        } else if (predicates.size() == 1) {
            results = cache.values(predicates.get(0));
        } else {
            Predicate<String, PaymentCacheDto> combined =
                    Predicates.and(predicates.toArray(new Predicate[0]));
            results = cache.values(combined);
        }

        log.info("searchPayments: status={}, merchantId={}, minAmount={} -> {} result(s)",
                status, merchantId, minAmount, results.size());

        return results.stream()
                .map(dto -> toResponse(dto, true))
                .collect(Collectors.toList());
    }

    /**
     * Update payment status using an EntryProcessor (PDF Chapter 4.3).
     *
     * The EntryProcessor runs ON the partition owner — no need to fetch the
     * full object to the client, mutate it, and send it back. We then
     * persist the change to PostgreSQL and refresh the cache.
     */
    @Transactional
    public PaymentResponse updateStatus(String txnId, StatusUpdateRequest request) {
        Payment payment = paymentRepository.findByTxnId(txnId)
                .orElseThrow(() -> new PaymentNotFoundException(txnId));

        payment.setStatus(request.getStatus());
        payment.setUpdatedBy(request.getUpdatedBy());
        Payment saved = paymentRepository.save(payment);

        IMap<String, PaymentCacheDto> cache = paymentCache();

        // If entry exists in cache, update it in place via EntryProcessor.
        // If not present, just write the fresh value.
        if (cache.containsKey(txnId)) {
            cache.executeOnKey(txnId, new PaymentStatusUpdater(request.getStatus(), request.getUpdatedBy()));
        } else {
            cache.put(txnId, PaymentCacheDto.from(saved), 10, TimeUnit.MINUTES);
        }

        // Broadcast status-change event
        paymentEventsTopic().publish(PaymentEvent.builder()
                .txnId(saved.getTxnId())
                .eventType("STATUS_UPDATED")
                .status(saved.getStatus())
                .amount(saved.getAmount())
                .timestamp(Instant.now())
                .build());

        log.info("Payment status updated: txnId={}, newStatus={}, updatedBy={}",
                txnId, request.getStatus(), request.getUpdatedBy());

        return toResponse(saved, false);
    }

    // ── Mapping helpers ──────────────────────────────────────

    private PaymentResponse toResponse(Payment payment, boolean fromCache) {
        return PaymentResponse.builder()
                .txnId(payment.getTxnId())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .servedFromCache(fromCache)
                .build();
    }

    private PaymentResponse toResponse(PaymentCacheDto dto, boolean fromCache) {
        return PaymentResponse.builder()
                .txnId(dto.getTxnId())
                .merchantId(dto.getMerchantId())
                .amount(dto.getAmount())
                .currency(dto.getCurrency())
                .status(dto.getStatus())
                .createdAt(dto.getCreatedAt())
                .updatedAt(dto.getUpdatedAt())
                .servedFromCache(fromCache)
                .build();
    }
}
