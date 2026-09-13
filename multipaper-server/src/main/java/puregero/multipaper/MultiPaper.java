package puregero.multipaper;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.maps.MapIndex;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.Plugin;
import org.purpurmc.purpur.util.MinecraftInternalPlugin;
import org.slf4j.Logger;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.externalserverprotocol.*;
import puregero.multipaper.mastermessagingprotocol.messages.masterbound.*;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.BooleanMessageReply;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.ChunkLoadedOnAnotherServerMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.DataMessageReply;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.ServerBoundMessage;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class MultiPaper {

    private static MultiPaperConnection multiPaperConnection = null;
    private static final Logger LOGGER = LogUtils.getClassLogger();
    public static final Plugin INTERNAL_PLUGIN = new MinecraftInternalPlugin();
    private static long last1Seconds = System.currentTimeMillis();
    private static long last10Seconds = System.currentTimeMillis();

    public static MultiPaperConnection getConnection() {
        if (multiPaperConnection == null) {
            multiPaperConnection = new MultiPaperConnection();
        }

        return multiPaperConnection;
    }

    public static void tick() {
        for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            if (player instanceof ExternalPlayer externalPlayer) {
                externalPlayer.connection.tickClientLoadTimeout();

                // Copied from Paper start - Configurable container update tick rate
                if (--externalPlayer.containerUpdateDelay <= 0) {
                    externalPlayer.containerMenu.broadcastChanges();
                    // Broadcast equipment and crafting slots when the player is in a container, fixes MC-297508
                    if (externalPlayer.containerMenu != externalPlayer.inventoryMenu) {
                        externalPlayer.inventoryMenu.broadcastNonContainerSlotChanges();
                    }
                    externalPlayer.containerUpdateDelay = externalPlayer.level().paperConfig().tickRates.containerUpdate;
                }
                // Copied from Paper end - Configurable container update tick rate

                externalPlayer.applyEffectsFromBlocks();

                externalPlayer.tickAttackStrengthAndItemSwap();

                if (player.takeXpDelay > 0) {
                    --player.takeXpDelay;
                }

                externalPlayer.tickDeathIfDead();

                if (externalPlayer.isSleeping()) {
                    externalPlayer.sleepCounter = Math.min(100, externalPlayer.sleepCounter + 1);
                } else {
                    externalPlayer.sleepCounter = 0;
                }
            }

            player.syncExperience();

            player.connection.reduceSpamCounters();
        }

        for (ExternalServer server : getConnection().getServersMap().values()) {
            if (server.getConnection() != null) {
                // This tick function must be run after the vanilla tick
                server.getConnection().tick();
            }
        }

        boolean hasBeen1Seconds = last1Seconds < System.currentTimeMillis() - 1000;

        if (hasBeen1Seconds) last1Seconds = System.currentTimeMillis();

        for (ServerLevel world : MinecraftServer.getServer().getAllLevels()) {
            SavedDataStorage storage = world.getDataStorage();
            for (Map.Entry<SavedDataType<?>, Optional<SavedData>> entry : storage.cache.entrySet()) {
                SavedDataType<?> type = entry.getKey();
                SavedData data = entry.getValue().orElse(null);

                if (data == null) {
                    continue;
                }

                if (!(data instanceof MapIndex || (data instanceof MapItemSavedData && hasBeen1Seconds))) {
                    continue;
                }

                storage.save(type, data);
            }
        }

        MultiPaperAckBlockChangesHandler.tick();

        MultiPaperInventoryHandler.tick();

        if (MinecraftServer.getServer().getTickCount() % 20 == 0) {
            for (ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
                if (level.dimensionType().defaultClock().isEmpty()) continue;
                broadcastPacketToExternalServers(new TimeUpdatePacket(level, false));
            }
        }

        RequestEntityPacket.tick();

        MultiPaperExternalBlocksHandler.tick();

        boolean hasBeen10Seconds = last10Seconds < System.currentTimeMillis() - 10000;

        if (hasBeen10Seconds) {
            last10Seconds = System.currentTimeMillis();

            MultiPaperStatHandler.sendIncreases();
        }

        MultiPaperPermissionSyncer.sync();
    }

    public static void sendTickTime(long time, double tps) {
        getConnection().send(new WriteTickTimeMessage(time, (float) tps));
    }

    public static CompletableFuture<Boolean> sendPlayerConnect(ServerPlayer player) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        getConnection().send(new PlayerConnectMessage(player.getUUID()), message -> future.complete(((BooleanMessageReply) message).result));
        return future;
    }

    public static void sendPlayerDisconnect(ServerPlayer player) {
        getConnection().send(new PlayerDisconnectMessage(player.getUUID()));
    }

    public static void onStart(SocketAddress bindAddress) {
        getConnection().send(new StartMessage(
                System.getProperty("server.address", ((InetSocketAddress) bindAddress).getAddress().getHostAddress()),
                ((InetSocketAddress) bindAddress).getPort()
        ));
    }

    public static void runSync(Runnable runnable) {
        if (MinecraftServer.getServer() == null) {
            // Wait a bit for the server to start up
            CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(() -> runSync(runnable));
            return;
        }

        MinecraftServer.getServer().scheduleOnMain(runnable);
    }

    public static void forEachExternalServer(Consumer<ExternalServer> externalServerConsumer) {
        getConnection().getServersMap().values().forEach(externalServerConsumer);
    }

    public static void broadcastPacketToExternalServers(ExternalServerPacket packet) {
        broadcastPacketToExternalServers(getConnection().getServersMap().values(), packet);
    }

    public static void broadcastPacketToExternalServers(Collection<ExternalServer> externalServers, ExternalServerPacket packet) {
        broadcastPacketToExternalServers(externalServers, () -> packet);
    }

    public static void broadcastPacketToExternalServers(Collection<ExternalServer> externalServers, Supplier<ExternalServerPacket> generatePacketIfNeeded) {
        if (!externalServers.isEmpty()) {
            ExternalServerPacket packet = generatePacketIfNeeded.get();
            externalServers.forEach(externalServer -> {
                if (!externalServer.isMe() && externalServer.getConnection() != null && externalServer.getConnection().isOpen()) {
                    externalServer.getConnection().send(packet);
                }
            });
        }
    }

    public static void broadcastPacketToExternalServers(UUID world, ExternalServerPacket packet) {
        forEachExternalServer(externalServer -> {
            if (externalServer.getConnection() != null && externalServer.getConnection().isOpen() && externalServer.getConnection().subscribedWorlds.contains(world)) {
                externalServer.getConnection().send(packet);
            }
        });
    }

    public static void broadcastPacketToExternalServers(ServerPlayer player, ExternalServerPacket packet) {
        if (player instanceof ExternalPlayer || player.didMultiPaperJoin) {
            broadcastPacketToExternalServers(player.level().getWorld().getUID(), packet);
        }
    }

    public static void onPlayerJoin(ServerPlayer player) {
        player.didMultiPaperJoin = true;
        PlayerCreatePacket.sendPlayer(player,
                getConnection().getServersMap().values().stream()
                        .map(ExternalServer::getConnection)
                        .filter(connection -> connection != null && connection.isOpen() && connection.subscribedWorlds.contains(player.level().uuid))
                        .toArray(ExternalServerConnection[]::new)
        );
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        if (!(player instanceof ExternalPlayer) && player.didMultiPaperJoin) {
            broadcastPacketToExternalServers(player, new PlayerRemovePacket(player.getUUID()));
        }
        if (MultiPaper.isRealPlayer(player)) {
            sendPlayerDisconnect(player);
        }
    }

    public static boolean isRealPlayer(Entity entity) {
        return entity instanceof ServerPlayer && !(entity instanceof ExternalPlayer);
    }

    public static boolean isRealPlayer(org.bukkit.entity.Entity bukkitEntity) {
        return isRealPlayer(((CraftEntity) bukkitEntity).getHandle());
    }

    public static boolean isExternalPlayer(Entity entity) {
        return entity instanceof ExternalPlayer;
    }

    public static boolean isExternalPlayer(org.bukkit.entity.Entity bukkitEntity) {
        return isExternalPlayer(((CraftEntity) bukkitEntity).getHandle());
    }

    public static byte[] nbtToBytes(CompoundTag compoundTag) throws IOException {
        if (compoundTag == null) {
            return new byte[0];
        }

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                NbtIo.write(compoundTag, out);
            }
            return buffer.toByteArray();
        }
    }

    public static CompoundTag nbtFromBytes(byte[] data) throws IOException {
        if (data.length == 0) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return NbtIo.read(in);
        }
    }

    public static CompletableFuture<Void> flushMasterConnectionAsync() {
        // Send and await a ping packet to ensure that all the queued packets in both directions have been fully flushed and handled
        return MultiPaper.getConnection().sendAndAwaitReply(new PingMessage(), BooleanMessageReply.class).thenRun(() -> {
        });
    }

    public static boolean isChunkExternal(Chunk chunk) {
        return chunk != null && ((CraftChunk) chunk).getHandle(ChunkStatus.EMPTY) instanceof LevelChunk levelChunk && isChunkExternal(levelChunk);
    }

    public static boolean isChunkExternal(LevelChunk chunk) {
        return chunk != null && isChunkExternal(chunk.moonrise$getChunkHolder());
    }

    public static boolean isChunkExternal(ServerLevel level, BlockPos pos) {
        return isChunkExternal(level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ChunkPos.pack(pos)));
    }

    public static boolean isChunkExternal(NewChunkHolder newChunkHolder) {
        return newChunkHolder != null && newChunkHolder.externalOwner != null && !newChunkHolder.externalOwner.isMe();
    }

    public static boolean isChunkLocal(Chunk chunk) {
        return chunk != null && ((CraftChunk) chunk).getHandle(ChunkStatus.EMPTY) instanceof LevelChunk levelChunk && isChunkLocal(levelChunk);
    }

    public static boolean isChunkLocal(LevelChunk chunk) {
        return chunk != null && isChunkLocal(chunk.moonrise$getChunkHolder());
    }

    public static boolean isChunkLocal(ServerLevel level, BlockPos pos) {
        return isChunkLocal(level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ChunkPos.pack(pos)));
    }

    public static boolean isChunkLocal(NewChunkHolder newChunkHolder) {
        return newChunkHolder != null && newChunkHolder.externalOwner != null && newChunkHolder.externalOwner.isMe();
    }

    public static CompletableFuture<CompoundTag> forceReadChunk(String world, String path, String type, int cx, int cz) {
        return getConnection().sendAndAwaitReply(new ForceReadChunkMessage(world, path, type, cx, cz), DataMessageReply.class).thenApply(message -> {
            try {
                return nbtDecompressFromBytes(message.data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static CompletableFuture<CompoundTag> readRegionFileNBTAsync(String world, String path, String type, int cx, int cz) {
        if (type.equals("region")) {
            World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null || MultiPaper.getChunkHolder(world, cx, cz) == null) {
                if (Bukkit.getPluginManager().getPlugin("Dynmap") == null) {
                    // Dynmap uses this, so don't log for Dynmap servers
                    LOGGER.warn("{} has no chunk holder for reading chunk {},{},{},{}, reading it straight from disk instead", Thread.currentThread(), world, type, cx, cz);
                }

                return forceReadChunk(world, path, type, cx, cz);
            }
        }

        return getConnection().sendAndAwaitReply(new ReadChunkMessage(world, path, type, cx, cz), ServerBoundMessage.class).thenCompose(message -> {
            if (message instanceof ChunkLoadedOnAnotherServerMessage chunkLoadedOnAnotherServerMessage) {
                ExternalServer server = getConnection().getServersMap().get(chunkLoadedOnAnotherServerMessage.server);
                CompletableFuture<CompoundTag> future = new CompletableFuture<>();
                if (server.getConnection() == null) {
                    // Don't throw the exception as that will cause the chunk to get corrupted and regenerate, losing data. Instead, allow the chunk loader to naturally timeout and try again.
                    LOGGER.error("Tried to request a chunk {},{},{},{} from {}, but we are not connected to them!", world, type, cx, cz, chunkLoadedOnAnotherServerMessage.server);
                } else if (type.equals("region")) {
                    server.getConnection().requestChunk(world, path, cx, cz, tag -> {
                        RequestChunkPacket.blocker = null;
                        future.complete(tag);
                    });
                } else if (type.equals("entities")) {
                    server.getConnection().requestEntities(world, path, cx, cz, future::complete);
                } else {
                    throw new IllegalArgumentException("Cannot load a " + type + " chunk from an external server");
                }
                return future;
            } else if (message instanceof DataMessageReply dataMessageReply) {
                try {
                    return CompletableFuture.completedFuture(nbtDecompressFromBytes(dataMessageReply.data));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new IllegalArgumentException("Unexpected message reply " + message);
            }
        });
    }

    public static CompoundTag readRegionFileNBT(String world, String path, String type, int cx, int cz) {
        try {
            return readRegionFileNBTAsync(world, path, type, cx, cz).get(20, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            LOGGER.warn("Timed out reading {},{},{},{}, retrying...", world, type, cx, cz);
            return readRegionFileNBT(world, path, type, cx, cz);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeRegionFileNBT(String world, String path, String type, int cx, int cz, CompoundTag compoundTag) throws IOException {
        getConnection().send(new WriteChunkMessage(world, path, type, cx, cz, nbtCompressToBytes(compoundTag), compoundTag != null && compoundTag.contains("multipaper.transient")), message -> { /* Do nothing */ });
    }

    public static CompoundTag readLevel(String world) throws IOException {
        byte[] data = getConnection().sendAndAwaitReply(new ReadLevelMessage(world), DataMessageReply.class).thenApply(message -> message.data).join();

        return data.length == 0 ? null : NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
    }

    public static void writeLevel(String world, CompoundTag compoundTag) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        NbtIo.writeCompressed(compoundTag, buffer);
        getConnection().send(new WriteLevelMessage(world, buffer.toByteArray()), message -> { /* do nothing */ });
    }

    public static String readJson(String name) throws IOException {
        if (MultiPaperConfiguration.get().syncSettings.syncJsonFiles) {
            return getConnection().sendAndAwaitReply(new ReadJsonMessage(name), DataMessageReply.class).thenApply(message -> new String(message.data, StandardCharsets.UTF_8)).join();
        } else if (new File(name).isFile()) {
            return Files.readString(new File(name).toPath());
        } else {
            return null;
        }
    }

    public static void writeJson(String name, String json) throws IOException {
        if (MultiPaperConfiguration.get().syncSettings.syncJsonFiles) {
            getConnection().send(new WriteJsonMessage(name, json.getBytes(StandardCharsets.UTF_8)), message -> { /* do nothing */ });
        } else {
            Files.writeString(new File(name).toPath(), json);
        }
    }

    public static byte[] readFile(String path) {
        return getConnection().sendAndAwaitReply(new ReadFileMessage(path), DataMessageReply.class).thenApply(message -> message.data).join();
    }

    public static void writeFile(String path, byte[] data) {
        getConnection().send(new WriteFileMessage(path, data), message -> { /* do nothing */ });
    }

    public static byte[] readData(String path, Identifier identifier) {
        if (getConnection().dataCache.containsKey(identifier)) {
            return getConnection().dataCache.remove(identifier);
        }

        return getConnection().sendAndAwaitReply(new ReadDataMessage(path), DataMessageReply.class).thenApply(message -> message.data).join();
    }

    public static void writeData(String path, Identifier identifier, CompoundTag compoundTag) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        NbtIo.writeCompressed(compoundTag, buffer);
        getConnection().send(new WriteDataMessage(path, identifier.toString(), buffer.toByteArray()), message -> { /* do nothing */ });
    }

    public static void lockChunk(NewChunkHolder newChunkHolder) {
        getConnection().send(new LockChunkMessage(newChunkHolder.world.getWorld().getName(), newChunkHolder.chunkX, newChunkHolder.chunkZ));
        newChunkHolder.hasExternalLockRequest = true;
    }

    public static void unlockChunk(NewChunkHolder newChunkHolder, ChunkAccess chunkAccess, ChunkEntitySlices chunkEntitySlices) {
        if (chunkAccess instanceof LevelChunk levelChunk && MultiPaper.isChunkLocal(newChunkHolder)) {
            if (chunkEntitySlices != null) {
                chunkEntitySlices.entities.forEach(MultiPaperEntitiesHandler::onEntityUnlock);
            }
            broadcastPacketToExternalServers(newChunkHolder.externalEntitiesSubscribers, () -> new SendEntitiesPacket(levelChunk, chunkEntitySlices));
            broadcastPacketToExternalServers(newChunkHolder.externalSubscribers, () -> new SendTickListPacket(levelChunk));
            levelChunk.getLevel().getRaids().getActiveRaid(levelChunk.getPos()).ifPresent(RaidUpdatePacket::broadcastUpdate);
            for (BlockEntity blockEntity : levelChunk.getBlockEntities().values()) {
                if (blockEntity instanceof Container container) {
                    List<HumanEntity> viewers = container.getViewers();
                    if (!viewers.isEmpty()) {
                        for (HumanEntity viewer : new ArrayList<>(container.getViewers())) {
                            if (viewer instanceof CraftPlayer craftPlayer) {
                                craftPlayer.closeInventory(InventoryCloseEvent.Reason.UNLOADED);
                            }
                        }
                    }
                }
            }
        }
        getConnection().send(new UnlockChunkMessage(newChunkHolder.world.getWorld().getName(), newChunkHolder.chunkX, newChunkHolder.chunkZ));
        newChunkHolder.externalOwner = null;
        newChunkHolder.hasExternalLockRequest = false;
    }

    public static void willSaveChunk(ServerLevel level, int x, int z) {
        getConnection().send(new WillSaveChunkLaterMessage(level.getWorld().getName(), x, z));
    }

    public static byte[] nbtCompressToBytes(CompoundTag compoundTag) throws IOException {
        if (compoundTag == null) {
            return new byte[0];
        }

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(buffer))) {
                NbtIo.write(compoundTag, out);
            }
            return buffer.toByteArray();
        }
    }

    public static CompoundTag nbtDecompressFromBytes(byte[] data) throws IOException {
        if (data.length == 0) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(data)))) {
            return NbtIo.read(in);
        }
    }

    public static ChunkAccess getChunkAccess(UUID world, int cx, int cz) {
        CraftWorld bukkitWorld = ((CraftWorld) Bukkit.getWorld(world));

        ChunkAccess chunkAccess = bukkitWorld != null ? bukkitWorld.getHandle().getChunkIfLoaded(cx, cz) : null;
        if (chunkAccess == null) {
            NewChunkHolder holder = getChunkHolder(world, cx, cz);
            if (holder != null) {
                chunkAccess = holder.getCurrentChunk();

                if (chunkAccess instanceof ImposterProtoChunk) {
                    chunkAccess = ((ImposterProtoChunk) chunkAccess).getWrapped();
                }
            }
        }

        return chunkAccess;
    }

    public static ChunkAccess getChunkAccess(UUID world, BlockPos pos) {
        return getChunkAccess(world, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static NewChunkHolder getChunkHolder(String world, BlockPos pos) {
        return getChunkHolder(world, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static NewChunkHolder getChunkHolder(UUID world, BlockPos pos) {
        return getChunkHolder(world, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static NewChunkHolder getChunkHolder(ServerLevel level, BlockPos pos) {
        return getChunkHolder(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static NewChunkHolder getChunkHolder(String world, int x, int z) {
        CraftWorld craftWorld = ((CraftWorld) Bukkit.getWorld(world));
        return craftWorld != null ? getChunkHolder(craftWorld.getHandle(), x, z) : null;
    }

    public static NewChunkHolder getChunkHolder(UUID world, int x, int z) {
        CraftWorld craftWorld = ((CraftWorld) Bukkit.getWorld(world));
        return craftWorld != null ? getChunkHolder(craftWorld.getHandle(), x, z) : null;
    }

    public static NewChunkHolder getChunkHolder(ServerLevel level, int x, int z) {
        return level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(x, z);
    }

    public static NewChunkHolder getChunkHolder(Entity entity) {
        return getChunkHolder((ServerLevel) entity.level(), entity.chunkPosition().x(), entity.chunkPosition().z());
    }

    public static void chunkChangedStatus(ServerLevel level, ChunkPos pos, ChunkStatus status) {
        getConnection().send(new ChunkChangedStatusMessage(level.getWorld().getName(), pos.x(), pos.z(), BuiltInRegistries.CHUNK_STATUS.getKey(status).toString()));
    }

    public static void setPort(int port) {
        getConnection().setPort(port);
    }
}
