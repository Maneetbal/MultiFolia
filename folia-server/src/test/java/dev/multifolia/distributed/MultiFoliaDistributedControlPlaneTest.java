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
