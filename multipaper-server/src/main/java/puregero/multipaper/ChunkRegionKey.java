package puregero.multipaper;

public record ChunkRegionKey(String world, String path, String type, int x, int z) {
    @Override
    public int hashCode() {
        int xTransform = 1664525 * this.x + 1013904223;
        int zTransform = 1664525 * (this.z ^ -559038737) + 1013904223;

        return this.world.hashCode() ^ this.path.hashCode() ^ this.type.hashCode() ^ xTransform ^ zTransform;
    }
}
