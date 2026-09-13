package puregero.multipaper.server.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class Async {
    public static CompletableFuture<Void> run(Runnable runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static CompletableFuture<Void> run(Runnable runnable, Executor executor) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, executor);
    }
}
