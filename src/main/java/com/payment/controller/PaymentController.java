package com.payment.controller;

import com.payment.dto.PaymentRequest;
import com.payment.dto.PaymentResponse;
import com.payment.dto.StatusUpdateRequest;
import com.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{txnId}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String txnId) {
        return ResponseEntity.ok(paymentService.getPayment(txnId));
    }

    @PatchMapping("/{txnId}/status")
    public ResponseEntity<PaymentResponse> updateStatus(
            @PathVariable String txnId,
            @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(paymentService.updateStatus(txnId, request));
    }

    /**
     * Predicate-based search over the payment-cache IMap (PDF Chapter 4.3).
     *
     * Examples:
     *   GET /api/payments/search?status=PENDING
     *   GET /api/payments/search?merchantId=M001
     *   GET /api/payments/search?status=FAILED&merchantId=M001
     *   GET /api/payments/search?minAmount=1000
     *
     * All parameters are optional and combined with AND when multiple
     * are supplied. Only entries currently in the IMap (within the
     * 10-minute TTL) are searched — this is a cache query, not a DB query.
     */
    @GetMapping("/search")
    public ResponseEntity<List<PaymentResponse>> searchPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) BigDecimal minAmount) {
        return ResponseEntity.ok(paymentService.searchPayments(status, merchantId, minAmount));
    }
}

