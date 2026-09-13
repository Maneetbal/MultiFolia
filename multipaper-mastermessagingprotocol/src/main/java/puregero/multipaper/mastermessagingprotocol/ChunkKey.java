package puregero.multipaper.mastermessagingprotocol;

public record ChunkKey(String world, int x, int z) {
    @Override
    public int hashCode() {
        int xTransform = 1664525 * this.x + 1013904223;
        int zTransform = 1664525 * (this.z ^ -559038737) + 1013904223;

        return this.world.hashCode() ^ xTransform ^ zTransform;
    }

    @Override
    public String toString() {
        return this.world + "," + this.x + "," + this.z;
    }
}
