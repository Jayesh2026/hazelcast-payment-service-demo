package com.payment.model;

import com.hazelcast.map.EntryProcessor;

import java.time.Instant;
import java.util.Map;

/**
 * EntryProcessor that updates a payment's status directly on the partition
 * owner — avoiding a separate get() + put() round trip (PDF Chapter 4.3,
 * "Entry Processors — Server-Side Execution").
 */
public class PaymentStatusUpdater implements EntryProcessor<String, PaymentCacheDto, Boolean> {

    private final String newStatus;
    private final String updatedBy;

    public PaymentStatusUpdater(String newStatus, String updatedBy) {
        this.newStatus = newStatus;
        this.updatedBy = updatedBy;
    }

    @Override
    public Boolean process(Map.Entry<String, PaymentCacheDto> entry) {
        PaymentCacheDto dto = entry.getValue();
        if (dto == null) {
            return false;
        }
        dto.setStatus(newStatus);
        dto.setUpdatedBy(updatedBy);
        dto.setUpdatedAt(Instant.now());
        entry.setValue(dto);
        return true;
    }
}
