package puregero.multipaper;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import ca.spottedleaf.moonrise.patches.starlight.light.SWMRNibbleArray;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.storage.TagValueInput;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.externalserverprotocol.SendUpdatePacket;
import puregero.multipaper.mastermessagingprotocol.messages.masterbound.SubscribeChunkMessage;
import puregero.multipaper.mastermessagingprotocol.messages.masterbound.UnsubscribeChunkMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.BooleanMessageReply;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Iterator;
import java.util.UUID;

public class MultiPaperChunkHandler {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    public static boolean shouldTick(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkIfLoaded(pos);
        return MultiPaper.isChunkLocal(chunk);
    }

    public static void onChunkLoad(LevelChunk chunk) {

    }

    public static void onChunkUnload(NewChunkHolder newChunkHolder, @Nullable ChunkAccess chunk, @Nullable ChunkEntitySlices chunkEntitySlices) {
        if (newChunkHolder.hasExternalLockRequest) {
            MultiPaper.unlockChunk(newChunkHolder, chunk, chunkEntitySlices);
        }
        MultiPaper.getConnection().sendAndAwaitReply(new UnsubscribeChunkMessage(newChunkHolder.world.getWorld().getName(), newChunkHolder.chunkX, newChunkHolder.chunkZ), BooleanMessageReply.class).thenRun(() ->
                onChunkUnsubscribed(newChunkHolder.world.getWorld().getName(), newChunkHolder.chunkX, newChunkHolder.chunkZ)
        );
    }

    public static void onChunkUnsubscribed(String world, int cx, int cz) {
        NewChunkHolder holder = MultiPaper.getChunkHolder(world, cx, cz);

        if (holder != null && holder.getCurrentChunk() != null) {
            LOGGER.warn("Chunk {},{},{} was unsubscribed from but has a chunk loaded! Resubscribing...", world, cx, cz);
            MultiPaper.getConnection().send(new SubscribeChunkMessage(world, cx, cz));
        }
    }

    public static void onBlockUpdate(NewChunkHolder newChunkHolder, Packet<? super ClientGamePacketListener> packet) {
        if (newChunkHolder == null) {
            // Chunk is still loading
            return;
        }

        ChunkAccess chunk = newChunkHolder.getCurrentChunk();

        if (chunk instanceof ImposterProtoChunk imposterProtoChunk) {
            chunk = imposterProtoChunk.getWrapped();
        }

        if (chunk == null) {
            LOGGER.warn("A {} occurred on an unloaded chunk {}", packet.getClass().getSimpleName(), newChunkHolder);
            return;
        }
        if (blockUpdateChunk == null) { // Don't broadcast the update to other servers if we're handling an update
            MultiPaper.broadcastPacketToExternalServers(newChunkHolder.externalSubscribers, () -> new SendUpdatePacket(newChunkHolder.world.uuid, packet));
        }
    }

    public static ChunkAccess blockUpdateChunk = null;
    private static NewChunkHolder holder = null;

    public static void handleBlockUpdate(UUID world, Packet<?> packet, int depth) {
        holder = null;
        blockUpdateChunk = null;
        ChunkAccess tempChunk = null;
        CraftWorld bukkitWorld = ((CraftWorld) Bukkit.getWorld(world));
        ServerLevel level = bukkitWorld != null ? bukkitWorld.getHandle() : null;
        if (level == null) {
            return;
        } else if (packet instanceof ClientboundBlockUpdatePacket update) {
            holder = MultiPaper.getChunkHolder(world, update.getPos());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket update) {
            update.runUpdates((pos, state) -> {
                if (holder == null) holder = MultiPaper.getChunkHolder(world, pos);
            });
        } else if (packet instanceof ClientboundBlockEntityDataPacket update) {
            holder = MultiPaper.getChunkHolder(world, update.getPos());
        } else if (packet instanceof ClientboundLightUpdatePacket update) {
            holder = MultiPaper.getChunkHolder(world, update.getX(), update.getZ());
        }

        if (holder != null && holder.hasGenerationTask()) {
            holder.onCurrentChunkLoaded().thenRun(() -> {
                if (!Bukkit.isPrimaryThread()) {
                    LOGGER.error("Loaded chunk {},{}, outside of the main thread! (currentThread={})", holder.chunkX, holder.chunkZ, Thread.currentThread(), new Throwable());
                }
                handleBlockUpdate(world, packet, depth);
            });
            return;
        }

        if (holder != null) {
            tempChunk = holder.getCurrentChunk();

            if (tempChunk instanceof ImposterProtoChunk imposterProtoChunk) {
                tempChunk = imposterProtoChunk.getWrapped();
            }
        }

        if (holder != null) {
            // Only send changes we make below to players, not other servers
            holder.vanillaChunkHolder.addChangesToPlayersOnly = true;
        }

        // Set blockUpdateChunk here so that we can broadcast changes beforehand
        blockUpdateChunk = tempChunk;

        if (holder != null && level.getChunkIfLoaded(holder.chunkX, holder.chunkZ) != null) {
            // Chunk is loaded
            if (packet instanceof ClientboundBlockUpdatePacket update) {
                setBlock(((LevelChunk) blockUpdateChunk), update.getPos(), update.getBlockState());
            } else if (packet instanceof ClientboundSectionBlocksUpdatePacket update) {
                update.runUpdates((pos, state) -> setBlock(((LevelChunk) blockUpdateChunk), pos, state));
            } else if (packet instanceof ClientboundBlockEntityDataPacket update) {
                BlockEntity existingBlockEntity = blockUpdateChunk.getBlockEntity(update.getPos());
                if (existingBlockEntity != null && existingBlockEntity.getType() == update.getType()) {
                    try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
                        existingBlockEntity.loadCustomOnly(TagValueInput.create(scopedCollector, level.registryAccess(), update.getTag()));
                    }
                    holder.vanillaChunkHolder.blockChanged(update.getPos());
                } else if (!blockUpdateChunk.getBlockState(update.getPos()).hasBlockEntity() && depth < 1) {
                    Bukkit.getScheduler().runTaskLater(MultiPaper.INTERNAL_PLUGIN, () -> handleBlockUpdate(world, packet, depth + 1), 1);
                } else {
                    blockUpdateChunk.removeBlockEntity(update.getPos());
                    blockUpdateChunk.setBlockEntityNbt(update.getTag());
                    blockUpdateChunk.getBlockEntity(update.getPos());
                    holder.vanillaChunkHolder.blockChanged(update.getPos());
                }
            }
        } else if (blockUpdateChunk != null) {
            // Chunk is not loaded
            if (packet instanceof ClientboundBlockUpdatePacket update) {
                setBlockInUnloadedChunk(blockUpdateChunk, update.getPos(), update.getBlockState());
            } else if (packet instanceof ClientboundSectionBlocksUpdatePacket update) {
                update.runUpdates((pos, state) -> setBlockInUnloadedChunk(blockUpdateChunk, pos, state));
            } else if (packet instanceof ClientboundBlockEntityDataPacket update) {
                blockUpdateChunk.removeBlockEntity(update.getPos());
                if (blockUpdateChunk instanceof LevelChunk levelChunk && levelChunk.blockEntitiesToLoad != null) {
                    levelChunk.blockEntitiesToLoad.add(update.getTag());
                } else {
                    blockUpdateChunk.setBlockEntityNbt(update.getTag());
                    blockUpdateChunk.getBlockEntity(update.getPos());
                }
            } else if (packet instanceof ClientboundLightUpdatePacket update) {
                handleLightUpdatePacket(level, blockUpdateChunk, update);
            }
        }

        if (holder != null) {
            holder.vanillaChunkHolder.addChangesToPlayersOnly = false;
        }

        blockUpdateChunk = null;
    }

    private static void setBlockInUnloadedChunk(ChunkAccess chunk, BlockPos pos, BlockState blockState) {
        if (chunk instanceof LevelChunk levelChunk) {
            levelChunk.setBlockState(pos, blockState, Block.UPDATE_SKIP_ON_PLACE);
        } else {
            chunk.setBlockState(pos, blockState, Block.UPDATE_ALL);
        }
    }

    private static void setBlock(LevelChunk chunk, BlockPos pos, BlockState blockState) {
        BlockState oldState = chunk.setBlockState(pos, blockState, Block.UPDATE_SKIP_ON_PLACE);
        holder.vanillaChunkHolder.blockChanged(pos);

        if (oldState != null && blockState != oldState && (blockState.getLightDampening() != oldState.getLightDampening() || blockState.getLightEmission() != oldState.getLightEmission() || blockState.useShapeForLightOcclusion() || oldState.useShapeForLightOcclusion())) {
            ProfilerFiller profilerFiller = Profiler.get();
            profilerFiller.push("queueCheckLightExternalUpdate");
            chunk.getLevel().getChunkSource().getLightEngine().checkBlock(pos);
            profilerFiller.pop();
        }
    }

    // From the client
    private static void handleLightUpdatePacket(ServerLevel level, ChunkAccess chunk, ClientboundLightUpdatePacket packet) {
        int i = packet.getX();
        int j = packet.getZ();
        LevelLightEngine levellightengine = level.getChunkSource().getLightEngine();
        BitSet bitset = packet.getLightData().getSkyYMask();
        BitSet bitset1 = packet.getLightData().getEmptySkyYMask();
        Iterator<byte[]> iterator = packet.getLightData().getSkyUpdates().iterator();
        readSectionList(chunk, i, j, levellightengine, LightLayer.SKY, bitset, bitset1, iterator);
        BitSet bitset2 = packet.getLightData().getBlockYMask();
        BitSet bitset3 = packet.getLightData().getEmptyBlockYMask();
        Iterator<byte[]> iterator1 = packet.getLightData().getBlockUpdates().iterator();
        readSectionList(chunk, i, j, levellightengine, LightLayer.BLOCK, bitset2, bitset3, iterator1);
    }

    // From the client
    private static void readSectionList(ChunkAccess chunk, int i, int j, LevelLightEngine levelLightEngine, LightLayer lightLayer, BitSet bitset2, BitSet bitset3, Iterator<byte[]> iterator1) {
        for (int k = 0; k < levelLightEngine.getLightSectionCount(); ++k) {
            int l = levelLightEngine.getMinLightSection() + k;
            boolean flag = bitset2.get(k);
            boolean flag1 = bitset3.get(k);
            if (flag || flag1) {
                if (lightLayer == LightLayer.BLOCK) {
                    chunk.starlight$getBlockNibbles()[k] = flag ? new SWMRNibbleArray(iterator1.next().clone()) : new SWMRNibbleArray();
                } else if (lightLayer == LightLayer.SKY) {
                    chunk.starlight$getSkyNibbles()[k] = flag ? new SWMRNibbleArray(iterator1.next().clone()) : new SWMRNibbleArray();
                }
            }
        }
    }
}
