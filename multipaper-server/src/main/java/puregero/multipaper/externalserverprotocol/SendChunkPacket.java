package puregero.multipaper.externalserverprotocol;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ChunkKey;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.function.Consumer;

public class SendChunkPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final String world;
    private final String path;
    private final int cx;
    private final int cz;
    private final CompoundTag tag;

    public SendChunkPacket(String world, String path, int cx, int cz, CompoundTag tag) {
        this.world = world;
        this.path = path;
        this.cx = cx;
        this.cz = cz;
        this.tag = tag;
    }

    public SendChunkPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUtf();
        this.path = in.readUtf();
        this.cx = in.readInt();
        this.cz = in.readInt();
        this.tag = in.readNbt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUtf(this.world);
        out.writeUtf(this.path);
        out.writeInt(this.cx);
        out.writeInt(this.cz);
        out.writeNbt(this.tag);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        Consumer<CompoundTag> callback = connection.chunkCallbacks.remove(new ChunkKey(this.world, this.cx, this.cz));
        if (callback != null) {
            if (this.tag == null) {
                LOGGER.warn("{} sent us an empty chunk for {},{},{}, force loading it from disk", connection.externalServer.getName(), this.world, this.cx, this.cz);
                MultiPaper.forceReadChunk(this.world, this.path, "region", this.cx, this.cz).thenAccept(callback);
            } else {
                callback.accept(this.tag);
            }
        } else {
            if (this.tag == null) {
                return;
            }

            CraftWorld craftWorld = ((CraftWorld) Bukkit.getWorld(this.world));
            ServerLevel level = craftWorld != null ? craftWorld.getHandle() : null;
            NewChunkHolder holder = MultiPaper.getChunkHolder(this.world, this.cx, this.cz);
            if (level == null) {
                LOGGER.warn("Received chunk data {},{},{} but we don't have world loaded.", this.world, this.cx, this.cz);
            } else if (holder == null) {
                LOGGER.warn("Received chunk data {},{},{} but no chunk is loaded here", this.world, this.cx, this.cz);
            } else if (holder.getCurrentChunk() instanceof LevelChunk) {
                LOGGER.warn("Received chunk data {},{},{} ({}), but it is a level chunk ({})", this.world, this.cx, this.cz, this.tag.getString("Status").orElseThrow(), holder.getCurrentGenStatus());
            } else {
                LOGGER.warn("Received chunk data {},{},{} ({}), but we have a {} chunk, forcing reload from disk.", this.world, this.cx, this.cz, this.tag.getString("Status").orElseThrow(), holder.getCurrentGenStatus());
                forceChunkUnsafeUnload(this.world, this.cx, this.cz);
            }
        }
    }

    public static void forceChunkUnsafeUnload(String world, int cx, int cz) {
        MultiPaper.runSync(() -> {
            ChunkPos pos = new ChunkPos(cx, cz);
            CraftWorld craftWorld = ((CraftWorld) Bukkit.getWorld(world));
            if (craftWorld != null) {
                ServerLevel level = craftWorld.getHandle();
                NewChunkHolder holder = level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(pos.longKey());

                if (holder != null) {
                    level.moonrise$getChunkTaskScheduler().chunkHolderManager.unloadChunkNowNoSave(holder);
                }
            }
        });
    }
}
