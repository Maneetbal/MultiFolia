package puregero.multipaper.externalserverprotocol;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RequestChunkPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    public static ExternalServer blocker = null;

    private final String world;
    private final String path;
    private final int cx;
    private final int cz;

    public RequestChunkPacket(String world, String path, int cx, int cz) {
        this.world = world;
        this.path = path;
        this.cx = cx;
        this.cz = cz;
    }

    public RequestChunkPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUtf();
        this.path = in.readUtf();
        this.cx = in.readInt();
        this.cz = in.readInt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUtf(this.world);
        out.writeUtf(this.path);
        out.writeInt(this.cx);
        out.writeInt(this.cz);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        World bukkitWorld = Bukkit.getWorld(this.world);

        if (!(bukkitWorld instanceof CraftWorld craftWorld)) {
            LOGGER.warn("{} is requesting chunk {},{},{} but we don't have the world {} loaded.", connection.externalServer.getName(), this.world, this.cx, this.cz, this.world);
            connection.send(new SendChunkPacket(this.world, this.path, this.cx, this.cz, null));
            return;
        }

        ServerLevel level = craftWorld.getHandle();
        NewChunkHolder newChunkHolder = MultiPaper.getChunkHolder(this.world, this.cx, this.cz);

        if (newChunkHolder == null) {
            LOGGER.warn("{} is requesting chunk {},{},{} but we aren't trying to load it.", connection.externalServer.getName(), this.world, this.cx, this.cz);
            connection.send(new SendChunkPacket(this.world, this.path, this.cx, this.cz, null));
            return;
        }

        CompletableFuture<ChunkAccess> future = newChunkHolder.onCurrentChunkLoaded();

        CompletableFuture<ChunkAccess> futureToWaitOn = future;
        if (blocker == connection.externalServer) {
            ChunkAccess access = newChunkHolder.getCurrentChunk();
            if (access != null) {
                futureToWaitOn = CompletableFuture.completedFuture(newChunkHolder.getCurrentChunk());
            } else {
                connection.send(new SendChunkPacket(this.world, this.path, this.cx, this.cz, null));
                return;
            }
        }

        futureToWaitOn.thenAccept(chunk -> {
                    if (future != newChunkHolder.onCurrentChunkLoaded()) {
                        // The future has been updated, try again
                        handle(connection);
                        return;
                    }

                    if (chunk == null) {
                        LOGGER.warn("{} is requesting chunk {},{},{} but we don't have it loaded.", connection.externalServer.getName(), this.world, this.cx, this.cz);
                        connection.send(new SendChunkPacket(this.world, this.path, this.cx, this.cz, null));
                        return;
                    }

                    try {
                        ListTag entitiesToLoad = null;
                        ListTag blockEntitiesToLoad = null;

                        ChunkAccess fullChunk = chunk instanceof ImposterProtoChunk imposterProtoChunk ? imposterProtoChunk.getWrapped() : chunk;

                        if (fullChunk instanceof LevelChunk levelChunk) {
                            // Cache these tags in case they get deleted while we serialize the chunk (multithreaded fun!)
                            entitiesToLoad = levelChunk.entitiesToLoad;
                            blockEntitiesToLoad = levelChunk.blockEntitiesToLoad;
                        }

                        CompoundTag tag = SerializableChunkData.copyOf(level, fullChunk).write();

                        if (entitiesToLoad != null) {
                            tag.put("entities", entitiesToLoad);
                        }
                        if (blockEntitiesToLoad != null) {
                            tag.put("block_entities", blockEntitiesToLoad);
                        }

                        connection.send(new SendChunkPacket(this.world, this.path, this.cx, this.cz, tag));
                    } catch (ConcurrentModificationException e) {
                        LOGGER.warn("Got ConcurrentModificationException while sending chunk, sending it in main thread instead");
                        MultiPaper.runSync(() -> handle(connection));
                    }
                })
                // Timeout instantly if this server is blocking our chunk loading, as this is probably also blocking their chunk loading
                .orTimeout(15, TimeUnit.SECONDS).exceptionally(throwable -> {
                    if (throwable instanceof TimeoutException) {
                        LOGGER.warn("Timed out while sending chunk {},{},{}", this.world, this.cx, this.cz);
                    } else {
                        LOGGER.warn("Error while sending chunk {},{},{}", this.world, this.cx, this.cz, throwable);
                    }

                    connection.send(new SendChunkPacket(this.world, this.path, this.cx, this.cz, null));
                    return null;
                });
    }
}
