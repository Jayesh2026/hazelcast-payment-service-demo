# Client-Server Mode — Setup Guide

This adds Client-Server Hazelcast (PDF Chapter 3.6–3.8) alongside the
existing Embedded mode. **Your `PaymentServiceImpl`, controllers, listener,
retry service, reference service — none of them changed.** Only config +
infrastructure changed.

---

## What changed (file-by-file)

| File | What / Why |
|---|---|
| `config/HazelcastMapNames.java` (NEW) | Shared map/topic/queue name constants, used by both profiles |
| `config/HazelcastConfig.java` | Added `@Profile("embedded")` — unchanged behavior otherwise |
| `config/HazelcastClientConfig.java` (NEW) | `@Profile("client-server")` — `ClientConfig` + `HazelcastClient` |
| `service/Impl/PaymentServiceImpl.java` | Now imports `HazelcastMapNames` instead of `HazelcastConfig` (cosmetic) |
| `application.properties` | `spring.profiles.active=${SPRING_PROFILES_ACTIVE:embedded}` — embedded by default, `server.port` now env-overridable |
| `application-client-server.properties` (NEW) | Cluster member addresses, datasource overrides for Docker |
| `hazelcast/hazelcast-server.xml` (NEW) | **Server-side** map configs (TTL, eviction, indexes, CP subsystem) — mounted into both Hazelcast containers |
| `Dockerfile` (NEW) | Multi-stage build for the Spring Boot app |
| `docker-compose.yml` (NEW) | 2 Hazelcast members + Management Center + Postgres + 2 app instances (clients) |

---

## How to run

### Embedded mode (unchanged — your existing workflow)
```bash
./gradlew bootRun
# or with a second instance:
./gradlew bootRun --args='--server.port=8082'
```
`spring.profiles.active` defaults to `embedded`, so nothing changes here.

### Client-Server mode (new)
```bash
docker-compose up -d --build
```

This starts:
- `hz-node1`, `hz-node2` — standalone Hazelcast members (port 5701, 5702 on host)
- `management-center` — http://localhost:8080 (admin / admin123)
- `postgres` — port 5432
- `payment-service-1` — http://localhost:8081 (Hazelcast CLIENT)
- `payment-service-2` — http://localhost:8082 (Hazelcast CLIENT)

Check the cluster formed:
```bash
docker logs hz-node1 | grep "Members"
# Members {size:2, ver:2} [
#   Member [hz-node1]:5701 - this
#   Member [hz-node2]:5701
# ]
```

Check the clients connected:
```bash
docker logs payment-svc-1 | grep -i "client.*connect\|HazelcastClient"
```
Open Management Center → "Clients" tab → you should see both
`payment-service-1` and `payment-service-2` listed (label: `payment-service`).

---

## ⚠️ Important caveat: PaymentCacheDto serialization

`PaymentCacheDto` implements `IdentifiedDataSerializable` (PDF Chapter 8.2).
This works perfectly in **Embedded mode** because the Hazelcast member
(your JVM) and the code that reads/writes the map are the **same JVM** —
the class is always on the classpath.

In **Client-Server mode**, the standalone Hazelcast containers
(`hazelcast/hazelcast:5.3.6` — plain, unmodified image) do **not** have
`com.payment.model.PaymentCacheDto` or `PaymentSerializationFactory` on
their classpath. When a client calls `cache.put(...)`, the member needs to
understand the byte format enough to store it (and especially to evaluate
**predicates/indexes** on `status` / `merchantId` — Chapter 4.3's
`searchPayments()`).

You have three realistic options. **Pick ONE before testing predicates
in client-server mode:**

### Option A (quickest for learning): Build a custom Hazelcast server image
Add a tiny Dockerfile that extends the official image and adds your
serialization classes as a JAR to its classpath:

```dockerfile
# hazelcast/Dockerfile
FROM hazelcast/hazelcast:5.3.6
COPY payment-serialization.jar /opt/hazelcast/userlib/
```

You'd build a small separate Gradle module (or extract just
`PaymentCacheDto` + `PaymentSerializationFactory` + `FailedPayment` +
`PaymentEvent` classes) into `payment-serialization.jar`, then point
`hz-node1`/`hz-node2` in `docker-compose.yml` at this custom image instead
of `hazelcast/hazelcast:5.3.6`.

### Option B (production-correct): Switch to Compact Serialization
PDF Chapter 8.1 lists **Compact** as the newest, schema-evolved format that
requires **no classes on the server at all** — the server stores data
generically using a schema. This is the recommended long-term fix and
involves changing `PaymentCacheDto` to implement `CompactSerializer`
instead of `IdentifiedDataSerializable`. (We can do this as a follow-up
step — it's a focused, contained change to one class + config registration.)

### Option C (fastest unblock, fewer features): Use plain `Serializable` + `GenericMapStore`-free maps for client-server testing
Temporarily change `PaymentCacheDto` to implement `java.io.Serializable`
only (remove `IdentifiedDataSerializable`). Java serialization needs no
server-side class registration for basic `put`/`get`, BUT — **predicates
and indexes on Java-serialized objects are unreliable/won't work** with
plain Java serialization in many Hazelcast versions, because the server
can't introspect fields without deserializing with your class.

---

## Recommended path
For now: run client-server mode and test `POST /api/payments`,
`GET /api/payments/{txnId}`, idempotency (409 on retry), and the
`/api/demo/payment-ref` (CP AtomicLong) endpoint — these primarily use
`get`/`put`/`putIfAbsent`/`incrementAndGet` and are less sensitive to this
issue than `searchPayments()`'s predicates.

When you're ready, tell me and we'll do **Option B (Compact
Serialization)** — it's the textbook-correct fix and a good next learning
step (PDF Chapter 8.1 calls it out as "the newest format... use for new
projects").
