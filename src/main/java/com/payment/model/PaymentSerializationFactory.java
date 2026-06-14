package com.payment.model;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

/**
 * Registers class IDs for IdentifiedDataSerializable classes used in the
 * payment-cache IMap (PDF Chapter 8.2).
 *
 * Must be registered in HazelcastConfig via:
 *   config.getSerializationConfig()
 *       .addDataSerializableFactory(FACTORY_ID, new PaymentSerializationFactory());
 */
public class PaymentSerializationFactory implements DataSerializableFactory {

    public static final int FACTORY_ID = 1;
    public static final int PAYMENT_CACHE_DTO_ID = 1;

    @Override
    public IdentifiedDataSerializable create(int typeId) {
        if (typeId == PAYMENT_CACHE_DTO_ID) {
            return new PaymentCacheDto();
        }
        throw new IllegalArgumentException("Unknown type id: " + typeId);
    }
}
