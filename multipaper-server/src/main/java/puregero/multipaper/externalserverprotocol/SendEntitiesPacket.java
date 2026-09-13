package puregero.multipaper.externalserverprotocol;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import com.mojang.logging.LogUtils;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ChunkKey;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperEntitiesHandler;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class SendEntitiesPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final String world;
    private final String path;
    private final int cx;
    private final int cz;
    private final CompoundTag tag;

    public SendEntitiesPacket(LevelChunk chunk) {
        this(chunk.getLevel(), chunk.getPos(), null);
    }

    public SendEntitiesPacket(LevelChunk chunk, ChunkEntitySlices chunkEntitySlices) {
        this(chunk.getLevel(), chunk.getPos(), chunkEntitySlices);
    }

    public SendEntitiesPacket(ServerLevel level, ChunkPos pos, ChunkEntitySlices chunkEntitySlices) {
        this(level.getWorld().getName(), level.moonrise$getEntityChunkDataController().getCache().folderStr, pos.x(), pos.z(), getEntities(level, pos, null, chunkEntitySlices));
    }

    public static CompoundTag getEntities(ServerLevel level, ChunkPos pos, @Nullable Consumer<ServerPlayer> foreachPlayer) {
        return getEntities(level, pos, foreachPlayer, null);
    }

    public static CompoundTag getEntities(ServerLevel level, ChunkPos pos, @Nullable Consumer<ServerPlayer> foreachPlayer, @Nullable ChunkEntitySlices chunkEntities) {
        if (chunkEntities == null) {
            chunkEntities = level.moonrise$getEntityLookup().getChunk(pos.x(), pos.z());
            if (chunkEntities == null) {
                LOGGER.error("Entities are not loaded in {}{}, sending null entities", level.getWorld().getName(), pos);
                return null;
            }
        }

        ListTag entities = new ListTag();
        for (Entity entity : chunkEntities.entities) {
            if (MultiPaperEntitiesHandler.shouldSyncEntity(entity)) {
                try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
                    TagValueOutput output = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());

                    entity.isSyncing = true;
                    entity.save(output);
                    entity.isSyncing = false;

                    entities.add(output.buildResult());
                }
            } else if (foreachPlayer != null && entity instanceof ServerPlayer player && MultiPaper.isRealPlayer(player)) {
                foreachPlayer.accept(player);
            }
        }
        CompoundTag entitiesRoot = NbtUtils.addCurrentDataVersion(new CompoundTag());
        entitiesRoot.put("Entities", entities);
        entitiesRoot.store("Position", ChunkPos.CODEC, pos);
        return entitiesRoot;
    }

    public SendEntitiesPacket(String world, String path, int cx, int cz, CompoundTag tag) {
        this.world = world;
        this.path = path;
        this.cx = cx;
        this.cz = cz;
        this.tag = tag;
    }

    public SendEntitiesPacket(RegistryFriendlyByteBuf in) {
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
        Consumer<CompoundTag> callback = connection.entitiesCallbacks.remove(new ChunkKey(this.world, this.cx, this.cz));
        if (callback != null) {
            if (this.tag == null) {
                LOGGER.warn("{} sent us an empty entities for {},{},{}, force loading it from disk", connection.externalServer.getName(), this.world, this.cx, this.cz);
                MultiPaper.forceReadChunk(this.world, this.path, "entities", this.cx, this.cz).thenAccept(callback);
            } else {
                callback.accept(this.tag);
            }
        } else {
            if (this.tag == null) {
                return;
            }

            // Replace the existing entities with these new entities
            ChunkPos pos = new ChunkPos(this.cx, this.cz);
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();
            // Check that we have these entities loaded in the first place
            if (level.moonrise$getEntityLookup().isChunkLoaded(pos)) {
                ListTag entities = this.tag.getList("Entities").orElseThrow();
                MultiPaper.runSync(() -> {
                    for (Tag entityTag : entities) {
                        try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
                            CompoundTag entityTagCompound = entityTag.asCompound().orElseThrow();
                            ValueInput input = TagValueInput.create(scopedCollector, level.registryAccess(), entityTagCompound);
                            EntityUpdateNBTPacket.loadEntity(level, input, input.read("UUID", UUIDUtil.CODEC).orElseThrow());
                        }
                    }
                });
            }
        }
    }
}
