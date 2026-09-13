#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

p = Path('settings.gradle.kts')
p.write_text(p.read_text(encoding='utf-8').replace('rootProject.name = "folia"', 'rootProject.name = "multifolia"'), encoding='utf-8')

p = Path('gradle.properties')
p.write_text(p.read_text(encoding='utf-8').replace('group=dev.folia', 'group=dev.multifolia'), encoding='utf-8')

Path('README.md').write_text('''# MultiFolia\n\nMultiFolia is a genuine Folia 26.2 fork. Folia remains the execution engine and its regionized multithreading, schedulers, and thread-ownership checks remain authoritative.\n\n## Distributed foundation\n\nThe first phase is a conservative control plane for worker registration, health, and single-writer region leases with fencing. Cross-worker live-region writes and world-file replication are disabled until safe state transfer exists.\n\n## Management\n\nThe management namespace is `/multifolia` with alias `/mf`; planned operations are `servers`, `list`, `debug`, and `map`. They remain observational/control-plane only and never bypass Folia scheduling or ownership.\n\n## Plugins\n\nPriority Folia-compatible targets: AuthMe, CombatLog, EasyHome, EzRTP, Floodgate, Geyser, GrimAC, LuckPerms, PacketEvents, PASF, PlaceholderAPI, RaycastedAntiESP, SkinsRestorer, SnowballDamage, TAB, ViaBackwards, ViaVersion. Axiom is excluded.\n''', encoding='utf-8')

Path('MULTIFOLIA_DISTRIBUTED_ARCHITECTURE.md').write_text('''# MultiFolia distributed architecture\n\nFolia remains authoritative for execution and thread ownership.\n\n- One worker owns a region at a time.\n- Every lease grant gets a monotonic fencing token.\n- Stale owners cannot renew or release.\n- Workers never directly write another worker's live region files.\n- Handoff requires explicit state transfer and acknowledgement before mutation.\n\nThe current phase is only the in-process control plane. Network transport, persistent state transfer, routing, and failure recovery are intentionally disabled until they can be integrated without bypassing Folia ownership semantics.\n''', encoding='utf-8')
PY

mkdir -p folia-server/src/main/java/dev/multifolia/distributed folia-server/src/test/java/dev/multifolia/distributed
cat > folia-server/src/main/java/dev/multifolia/distributed/MultiFoliaDistributedControlPlane.java <<'EOF'
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
EOF

cat > folia-server/src/test/java/dev/multifolia/distributed/MultiFoliaDistributedControlPlaneTest.java <<'EOF'
package dev.multifolia.distributed;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MultiFoliaDistributedControlPlaneTest {
    @Test void ownershipIsSingleWriterAndFenced() {
        var cp = new MultiFoliaDistributedControlPlane();
        cp.registerWorker("a", "local-a");
        cp.registerWorker("b", "local-b");
        var key = new MultiFoliaDistributedControlPlane.RegionKey("minecraft:overworld", 1, 2);
        var first = cp.tryAcquire(key, "a", Duration.ofSeconds(5)).orElseThrow();
        assertTrue(cp.tryAcquire(key, "b", Duration.ofSeconds(5)).isEmpty());
        assertTrue(cp.release(key, "a", first.fencingToken()));
        var second = cp.tryAcquire(key, "b", Duration.ofSeconds(5)).orElseThrow();
        assertTrue(second.fencingToken() > first.fencingToken());
        assertFalse(cp.release(key, "a", first.fencingToken()));
    }
}
EOF

rm -rf multipaper-api multipaper-server multipaper-master multipaper-mastermessagingprotocol
rm -f DEVELOPING_A_MULTISERVER_PLUGIN.md assets/multifolia-diagram.jpg
rmdir assets 2>/dev/null || true

test -d folia-api
test -d folia-server
test -d folia-checkstyle
test -d build-data
test ! -d multipaper-api
test ! -d multipaper-server
test ! -d multipaper-master
test ! -d multipaper-mastermessagingprotocol
grep -q '^mcVersion=26.2$' gradle.properties
grep -q '^apiVersion=26.2$' gradle.properties
grep -q '^paperRef=5d4f9bd0e4f6b1ef70d07feb411580cef7d1a746$' gradle.properties
grep -Rql 'RegionScheduler' folia-api folia-server
