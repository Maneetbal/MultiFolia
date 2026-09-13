package dev.multifolia.distributed;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MultiFoliaDistributedControlPlane {
    public enum WorkerState { REGISTERED, HEALTHY, DRAINING, UNHEALTHY, OFFLINE }
    public record Worker(String id, WorkerState state, Instant lastHeartbeat, String endpoint) {
        public Worker { Objects.requireNonNull(id); Objects.requireNonNull(state); Objects.requireNonNull(lastHeartbeat); }
        public Worker heartbeat(Instant now) { return new Worker(id, WorkerState.HEALTHY, now, endpoint); }
    }
    public record RegionKey(String worldKey, int regionX, int regionZ) {
        public RegionKey { Objects.requireNonNull(worldKey); }
    }
    public record Lease(String workerId, long fencingToken, long expiresAtNanos) {
        public boolean expired(long now) { return now >= expiresAtNanos; }
    }
    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final Map<RegionKey, Lease> leases = new ConcurrentHashMap<>();
    private final AtomicLong fencing = new AtomicLong();

    public Worker registerWorker(String id, String endpoint) {
        Instant now = Instant.now();
        return workers.compute(id, (k, old) -> old == null ? new Worker(id, WorkerState.REGISTERED, now, endpoint) : old.heartbeat(now));
    }
    public Worker heartbeat(String id) {
        Worker worker = workers.computeIfPresent(id, (k, old) -> old.heartbeat(Instant.now()));
        if (worker == null) throw new IllegalArgumentException("Unknown worker: " + id);
        return worker;
    }
    public List<Worker> workers() { return workers.values().stream().sorted((a,b) -> a.id().compareTo(b.id())).toList(); }
    public Optional<Lease> current(RegionKey key) {
        Lease lease = leases.get(key);
        if (lease == null) return Optional.empty();
        if (lease.expired(System.nanoTime())) { leases.remove(key, lease); return Optional.empty(); }
        return Optional.of(lease);
    }
    public Optional<Lease> tryAcquire(RegionKey key, String workerId, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        if (!workers.containsKey(workerId)) throw new IllegalArgumentException("Unknown worker: " + workerId);
        long now = System.nanoTime();
        long expiry = Math.addExact(now, ttl.toNanos());
        Lease[] acquired = new Lease[1];
        leases.compute(key, (k, old) -> {
            if (old != null && !old.expired(now) && !old.workerId().equals(workerId)) return old;
            Lease lease = new Lease(workerId, fencing.incrementAndGet(), expiry);
            acquired[0] = lease;
            return lease;
        });
        return Optional.ofNullable(acquired[0]);
    }
    public boolean renew(RegionKey key, String workerId, long token, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        long expiry = Math.addExact(System.nanoTime(), ttl.toNanos());
        boolean[] ok = new boolean[1];
        leases.computeIfPresent(key, (k, old) -> {
            if (old.workerId().equals(workerId) && old.fencingToken() == token && !old.expired(System.nanoTime())) {
                ok[0] = true;
                return new Lease(workerId, token, expiry);
            }
            return old;
        });
        return ok[0];
    }
    public boolean release(RegionKey key, String workerId, long token) {
        Lease old = leases.get(key);
        return old != null && old.workerId().equals(workerId) && old.fencingToken() == token && leases.remove(key, old);
    }
}
