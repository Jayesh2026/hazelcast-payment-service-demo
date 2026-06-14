package com.payment.config;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.config.NearCacheConfig;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Hazelcast CLIENT configuration — used when the Hazelcast cluster runs as
 * separate, standalone member nodes (PDF Chapter 3.6 / 3.7).
 *
 * Active when spring.profiles.active=client-server.
 *
 * IMPORTANT: in this mode, your Spring Boot JVM is a CLIENT, not a member.
 *  - It holds NO data, owns NO partitions, is invisible to cluster topology.
 *  - Map configuration (TTL, eviction, indexes, backups, CP subsystem) lives
 *    on the SERVER side now — see hazelcast/hazelcast-server.xml, which is
 *    mounted into the standalone Hazelcast containers via docker-compose.
 *  - This class only configures: cluster connection details + the client's
 *    own Near Cache (a local read-cache layered on top of the remote IMap).
 *
 * PaymentServiceImpl, controllers, listeners — ZERO changes. They all just
 * call hazelcastInstance.getMap(...) / getTopic(...) / getQueue(...) /
 * getCPSubsystem() exactly as before. The HazelcastInstance interface is
 * identical whether it's backed by a member or a client.
 */
@Configuration
@Profile("client-server")
public class HazelcastClientConfig {

    public static final String PAYMENT_CACHE   = HazelcastMapNames.PAYMENT_CACHE;
    public static final String IDEMPOTENCY_MAP = HazelcastMapNames.IDEMPOTENCY_MAP;

    /**
     * Comma-separated list of Hazelcast member addresses, e.g.
     *   hz-node1:5701,hz-node2:5701
     * Supplied via application-client-server.properties (and overridable
     * via the HAZELCAST_CLIENT_ADDRESSES env var in docker-compose.yml).
     */
    @Value("${hazelcast.client.cluster-members}")
    private String clusterMembers;

    @Value("${hazelcast.client.cluster-name:payment-cluster}")
    private String clusterName;

    @Bean
    public ClientConfig hazelcastClientConfig() {
        ClientConfig clientConfig = new ClientConfig();

        // Must match cluster-name on the standalone server nodes
        // (see hazelcast/hazelcast-server.xml).
        clientConfig.setClusterName(clusterName);

        // ── Network: list ALL server member addresses ──────────
        ClientNetworkConfig network = clientConfig.getNetworkConfig();
        for (String address : clusterMembers.split(",")) {
            network.addAddress(address.trim());
        }
        network.setConnectionTimeout(5000); // 5s per connect attempt

        // Retry connecting for up to 120s before giving up (e.g. while
        // Hazelcast containers are still starting up in docker-compose)
        clientConfig.getConnectionStrategyConfig()
                .getConnectionRetryConfig()
                .setClusterConnectTimeoutMillis(120_000);

        // ── Client-side Near Cache for payment-cache ────────────
        // This is purely local to THIS client JVM. It does not replace
        // the server-side map config — it sits on top of it.
        NearCacheConfig nearCache = new NearCacheConfig(PAYMENT_CACHE);
        nearCache.setTimeToLiveSeconds(60);
        nearCache.setMaxIdleSeconds(30);
        nearCache.setInvalidateOnChange(true); // server pushes invalidations
        clientConfig.addNearCacheConfig(nearCache);

        // Label visible in Hazelcast Management Center
        clientConfig.addLabel("payment-service");

        // ── Serialization — must match the server-side factory ──
        // (PDF Chapter 8.2). The server nodes also need PaymentCacheDto's
        // class on their classpath OR Compact/Portable serialization —
        // for this learning setup we keep IdentifiedDataSerializable and
        // ensure the same factory registration on both sides.
        clientConfig.getSerializationConfig()
                .addDataSerializableFactory(
                        com.payment.model.PaymentSerializationFactory.FACTORY_ID,
                        new com.payment.model.PaymentSerializationFactory()
                );

        return clientConfig;
    }

    /**
     * Returns a CLIENT HazelcastInstance (not a member node).
     * Same interface as the embedded Hazelcast.newHazelcastInstance(config)
     * bean — PaymentServiceImpl etc. don't know or care which one this is.
     */
    @Bean
    public HazelcastInstance hazelcastInstance(ClientConfig clientConfig) {
        return HazelcastClient.newHazelcastClient(clientConfig);
    }
}
