package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ProtoChunkTicks;
import net.minecraft.world.ticks.SavedTick;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.List;
import java.util.UUID;

public class SendTickListPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static final Codec<List<SavedTick<Block>>> BLOCK_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.BLOCK.byNameCodec()).listOf();
    private static final Codec<List<SavedTick<Fluid>>> FLUID_TICKS_CODEC = SavedTick.codec(BuiltInRegistries.FLUID.byNameCodec()).listOf();

    private final UUID world;
    private final int cx;
    private final int cz;
    private final CompoundTag tag;

    public SendTickListPacket(LevelChunk chunk) {
        this.world = chunk.getLevel().uuid;
        this.cx = chunk.locX;
        this.cz = chunk.locZ;

        this.tag = new CompoundTag();

        long gameTime = chunk.getLevel().getGameTime();
        this.tag.store("block_ticks", BLOCK_TICKS_CODEC, chunk.blockTicks.pack(gameTime));
        this.tag.store("fluid_ticks", FLUID_TICKS_CODEC, chunk.fluidTicks.pack(gameTime));
    }

    public SendTickListPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.cx = in.readInt();
        this.cz = in.readInt();
        this.tag = in.readNbt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeInt(this.cx);
        out.writeInt(this.cz);
        out.writeNbt(this.tag);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ChunkPos pos = new ChunkPos(this.cx, this.cz);
            CraftWorld bukkitWorld = ((CraftWorld) Bukkit.getWorld(this.world));
            ServerLevel level = bukkitWorld != null ? bukkitWorld.getHandle() : null;
            ChunkAccess chunk = MultiPaper.getChunkAccess(this.world, this.cx, this.cz);
            List<SavedTick<Block>> parsedBlockTicks = SavedTick.filterTickListForChunk(this.tag.read("block_ticks", BLOCK_TICKS_CODEC).orElseThrow(), pos);
            List<SavedTick<Fluid>> parsedFluidTicks = SavedTick.filterTickListForChunk(this.tag.read("fluid_ticks", FLUID_TICKS_CODEC).orElseThrow(), pos);
            if (level != null && level.getChunkIfLoaded(this.cx, this.cz) != null) {
                long now = level.getGameTime();

                LevelChunkTicks<Block> blockTicks = new LevelChunkTicks<>(parsedBlockTicks);
                blockTicks.unpack(now);
                blockTicks.removeIf(scheduled -> {
                    level.getBlockTicks().schedule(scheduled);
                    return true;
                });

                LevelChunkTicks<Fluid> fluidTicks = new LevelChunkTicks<>(parsedFluidTicks);
                fluidTicks.unpack(now);
                fluidTicks.removeIf(scheduled -> {
                    level.getFluidTicks().schedule(scheduled);
                    return true;
                });
            } else if (level != null && chunk instanceof LevelChunk levelChunk) {
                levelChunk.unregisterTickContainerFromLevel(level);
                levelChunk.blockTicks = new LevelChunkTicks<>(parsedBlockTicks);
                levelChunk.fluidTicks = new LevelChunkTicks<>(parsedFluidTicks);
                levelChunk.unpackTicks(level.getGameTime());
                if (levelChunk.loaded) levelChunk.registerTickContainerInLevel(level);
            } else if (chunk instanceof ProtoChunk protoChunk) {
                protoChunk.blockTicks = ProtoChunkTicks.load(parsedBlockTicks);
                protoChunk.fluidTicks = ProtoChunkTicks.load(parsedFluidTicks);
            } else {
                LOGGER.warn("Received tick lists for an unloaded chunk {},{},{}", this.world, this.cx, this.cz);
            }
        });
    }
}
