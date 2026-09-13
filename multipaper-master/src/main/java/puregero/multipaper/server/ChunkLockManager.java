package puregero.multipaper.server;

import puregero.multipaper.mastermessagingprotocol.ChunkKey;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChunkLockManager implements LockManager {
    private final ConcurrentHashMap<ChunkKey, CompletableFuture<Void>> locks = new ConcurrentHashMap<>();

    @Override
    public void lockUntilWrite(String world, int cx, int cz) {
        this.locks.put(new ChunkKey(world, cx, cz), new CompletableFuture<Void>().completeOnTimeout(null, 60, TimeUnit.SECONDS));
    }

    @Override
    public void writtenChunk(String world, int cx, int cz) {
        CompletableFuture<Void> lock = this.locks.remove(new ChunkKey(world, cx, cz));

        if (lock != null) {
            lock.complete(null);
        }
    }

    @Override
    public void waitForLock(String world, int cx, int cz, Runnable callback) {
        CompletableFuture<Void> lock = this.locks.get(new ChunkKey(world, cx, cz));

        if (lock != null) {
            lock.thenRun(callback);
        } else {
            callback.run();
        }
    }

}
