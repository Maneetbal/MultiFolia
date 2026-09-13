package puregero.multipaper.server;

public interface LockManager {
    LockManager POI_MANAGER = new LockManager() {
        @Override
        public void lockUntilWrite(String world, int cx, int cz) {
        }

        @Override
        public void writtenChunk(String world, int cx, int cz) {
        }

        @Override
        public void waitForLock(String world, int cx, int cz, Runnable callback) {
            callback.run();
        }
    };
    LockManager CHUNK_MANAGER = new ChunkLockManager();
    LockManager ENTITIES_MANAGER = new EntitiesLockManager();

    static LockManager getLockManager(String type) {
        return switch (type) {
            case "poi" -> POI_MANAGER;
            case "region" -> CHUNK_MANAGER;
            case "entities" -> ENTITIES_MANAGER;
            default -> throw new IllegalArgumentException("Unknown data type " + type);
        };
    }

    void lockUntilWrite(String world, int cx, int cz);

    void writtenChunk(String world, int cx, int cz);

    void waitForLock(String world, int cx, int cz, Runnable callback);
}
