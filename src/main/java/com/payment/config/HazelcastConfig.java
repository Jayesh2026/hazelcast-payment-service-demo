package com.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.IndexConfig;
import com.hazelcast.config.IndexType;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * Embedded Hazelcast configuration (Spring Boot JVM IS a cluster member).
 *
 * Maps to PDF Chapter 3.2 — "Hazelcast Configuration — Java Config".
 * Active when spring.profiles.active=embedded (the default profile —
 * see application.properties: spring.profiles.active=embedded).
 *
 * Two IMaps are configured:
 *  - payment-cache   : fast read-through cache for Payment lookups (TTL 10 min)
 *  - idempotency-keys: atomic claim map preventing duplicate payment processing (TTL 24h)
 *
 * In this mode, THIS class fully owns map configuration (TTL, eviction,
 * indexes, near cache) because your app's Config object defines how the
 * embedded cluster member behaves.
 */
@Configuration
@Profile("embedded")
public class HazelcastConfig {

    public static final String PAYMENT_CACHE   = HazelcastMapNames.PAYMENT_CACHE;
    public static final String IDEMPOTENCY_MAP = HazelcastMapNames.IDEMPOTENCY_MAP;

    @Bean
    public Config hazelcastConfiguration() {
        Config config = new Config();
        config.setInstanceName("payment-hazelcast-instance");
        config.setClusterName("payment-cluster");       // by default cluster name dev
       
        // --- Network Configuration ---
        NetworkConfig network = config.getNetworkConfig();
        network.setPort(5701);
        network.setPortAutoIncrement(true); // if 5701 busy, try 5702, 5703...
        network.setPortCount(10);

        // ── Discovery: TCP/IP with localhost (good for local/dev) ──
        // In production with multiple app instances on different hosts,
        // list each host's IP under member-list (or use a discovery plugin).
        JoinConfig join = network.getJoin();
        join.getMulticastConfig().setEnabled(false); // disable multicast discovery
        join.getTcpIpConfig()
            .setEnabled(true)
            .addMember("127.0.0.1");

        // ── IMap configurations ─────────────────────────────
        config.addMapConfig(paymentCacheConfig());
        config.addMapConfig(idempotencyConfig());

        // ── CP Subsystem: required for getCPSubsystem().getAtomicLong(...) ──
        // (PDF Chapter 5.5 — strongly consistent counters via RAFT consensus)
        // CP_MEMBER_COUNT must be an odd number >= 3 for real fault tolerance.
        // For local single/dual-instance dev/demo, 1 is the minimum that
        // lets the CP Subsystem initialize at all.
        config.getCPSubsystemConfig().setCPMemberCount(0);

        // ── Serialization: register IdentifiedDataSerializable factory ──
        // Maps to PDF Chapter 8.2 — PaymentCacheDto uses this factory.
        config.getSerializationConfig()
                .addDataSerializableFactory(
                        com.payment.model.PaymentSerializationFactory.FACTORY_ID,
                        new com.payment.model.PaymentSerializationFactory()
                );

        return config;
    }

    /**
     * payment-cache: holds Payment lookups for fast reads.
     * - TTL 10 minutes, idle eviction after 5 minutes
     * - LRU eviction, max 50,000 entries per node
     * - Near Cache enabled (Chapter 9.2): fast local reads, invalidated on change
     * - Indexes on status & merchantId for predicate queries (Chapter 4.3)
     */
    private MapConfig paymentCacheConfig() {
        MapConfig mapConfig = new MapConfig(PAYMENT_CACHE);
        mapConfig.setTimeToLiveSeconds(600);   // 10 minutes
        mapConfig.setMaxIdleSeconds(300);      // 5 minutes
        mapConfig.setBackupCount(1);
        mapConfig.setAsyncBackupCount(0);

        mapConfig.setEvictionConfig(
                new EvictionConfig()
                        .setEvictionPolicy(EvictionPolicy.LRU)
                        .setMaxSizePolicy(MaxSizePolicy.PER_NODE)
                        .setSize(50_000)
        );

        // Near Cache — local read-through cache, invalidated on remote changes
        NearCacheConfig nearCache = new NearCacheConfig(PAYMENT_CACHE);
        nearCache.setTimeToLiveSeconds(60);
        nearCache.setMaxIdleSeconds(30);
        nearCache.setInvalidateOnChange(true);
        mapConfig.setNearCacheConfig(nearCache);

        // Indexes for predicate queries (e.g. find all FAILED payments for a merchant)
        mapConfig.addIndexConfig(new IndexConfig(IndexType.HASH, "status"));
        mapConfig.addIndexConfig(new IndexConfig(IndexType.HASH, "merchantId"));

        return mapConfig;
    }

    /**
     * idempotency-keys: atomic claim map for the idempotency pattern (Chapter 9.1).
     * TTL 24h, 2 backups (extra safety for the idempotency guarantee).
     */
    private MapConfig idempotencyConfig() {
        MapConfig mapConfig = new MapConfig(IDEMPOTENCY_MAP);
        mapConfig.setTimeToLiveSeconds(86_400); // 24 hours
        mapConfig.setBackupCount(2);
        return mapConfig;
    }


    /**
     * Creates the embedded Hazelcast member instance.
     * This JVM becomes a Hazelcast cluster MEMBER (see PDF section 2.1 / 2.6).
     */
    @Bean
    public HazelcastInstance hazelcastInstance(Config config) {
        return Hazelcast.newHazelcastInstance(config);
    }

}
