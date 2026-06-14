package com.payment.model;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lightweight representation of a Payment, stored inside the
 * Hazelcast "payment-cache" IMap.
 *
 * Implements IdentifiedDataSerializable (PDF Chapter 8.2) for fast,
 * compact serialization across cluster members — avoids the overhead
 * of Java's built-in Serializable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCacheDto implements IdentifiedDataSerializable {

    private String txnId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public static PaymentCacheDto from(Payment payment) {
        return PaymentCacheDto.builder()
                .txnId(payment.getTxnId())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .updatedBy(payment.getUpdatedBy())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    @Override
    public int getFactoryId() {
        return PaymentSerializationFactory.FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return PaymentSerializationFactory.PAYMENT_CACHE_DTO_ID;
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeString(txnId);
        out.writeString(merchantId);
        out.writeString(amount.toPlainString());
        out.writeString(currency);
        out.writeString(status);
        out.writeString(updatedBy);
        out.writeLong(createdAt != null ? createdAt.toEpochMilli() : -1L);
        out.writeLong(updatedAt != null ? updatedAt.toEpochMilli() : -1L);
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
        this.txnId      = in.readString();
        this.merchantId = in.readString();
        this.amount     = new BigDecimal(in.readString());
        this.currency   = in.readString();
        this.status     = in.readString();
        this.updatedBy  = in.readString();
        long created = in.readLong();
        long updated = in.readLong();
        this.createdAt = created == -1L ? null : Instant.ofEpochMilli(created);
        this.updatedAt = updated == -1L ? null : Instant.ofEpochMilli(updated);
    }
}
