package puregero.multipaper.externalserverprotocol;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class RequestEntityPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static final Map<Pair<UUID, ExternalServerConnection>, Integer> playerRequestCounts = new HashMap<>();
    private static final Set<Triple<String, UUID, UUID>> requestedEntitiesThisTick = new HashSet<>();

    private final UUID world;
    private final UUID uuid;

    public static void tick() {
        requestedEntitiesThisTick.clear();
    }

    public static void requestEntity(ExternalServerConnection connection, UUID world, UUID entity) {
        if (!hasAlreadyRequestedThisTick(connection, world, entity)) {
            connection.send(new RequestEntityPacket(world, entity));
        }
    }

    private static boolean hasAlreadyRequestedThisTick(ExternalServerConnection connection, UUID world, UUID entity) {
        if (!Bukkit.isPrimaryThread()) {
            LOGGER.warn("RequestEntityPacket.requestEntity called off main thread, sending packet async without checks");
            return false; // We can't check this async, so just send the packet anyway
        } else {
            return !requestedEntitiesThisTick.add(Triple.of(connection.externalServer.getName(), world, entity));
        }
    }

    private RequestEntityPacket(UUID world, UUID uuid) {
        this.world = world;
        this.uuid = uuid;
    }

    public RequestEntityPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            CraftWorld craftWorld = ((CraftWorld) Bukkit.getWorld(this.world));
            if (craftWorld == null) return;
            ServerLevel level = craftWorld.getHandle();
            Entity entity = level.moonrise$getEntityLookup().getEntityIgnoringAccessible(this.uuid);
            if (entity != null) {
                if (entity.isFake()) return;
                if (entity instanceof ServerPlayer serverPlayer) {
                    triedToRequestPlayer(connection, serverPlayer);
                    return;
                }

                // Send the vehicle the entity is in
                entity = entity.getRootVehicle();

                // Check that the server is subscribed to the entity's chunk
                Optional<NewChunkHolder> chunkHolder = Optional.ofNullable(level.getChunkSource().chunkMap.getVisibleChunkIfPresent(entity.chunkPosition().longKey())).map(ChunkHolder::moonrise$getRealChunkHolder);

                if (chunkHolder.filter(holder -> holder.externalEntitiesSubscribers.contains(connection.externalServer)).isEmpty()) {
                    LOGGER.warn("{} requested entity {}, but that entity is not in a chunk that server is subscribed to ({},{},{})", connection.externalServer.getName(), this.uuid, craftWorld.getName(), entity.chunkPosition().x(), entity.chunkPosition().z());
                    return;
                }

                EntityUpdateWithDependenciesPacket.sendVehicleAndPassengersPacketsRecursivelyToServers(entity, List.of(connection.externalServer));
            } else {
                LOGGER.warn("{} requested entity {}, but that entity doesn't exist", connection.externalServer.getName(), this.uuid);
            }
        });
    }

    private void triedToRequestPlayer(ExternalServerConnection connection, ServerPlayer serverPlayer) {
        if (!MinecraftServer.getServer().getPlayerList().getPlayers().contains(serverPlayer)) return;
        Pair<UUID, ExternalServerConnection> key = Pair.of(serverPlayer.getUUID(), connection);
        int count = playerRequestCounts.getOrDefault(key, 0);
        playerRequestCounts.put(key, count + 1);
        if (count > 10) {
            LOGGER.error("{} tried to request player {} more than 10 times! This means they didn't sync correctly. Kicking them.", connection.externalServer.getName(), serverPlayer.getScoreboardName());
            serverPlayer.getBukkitEntity().kickPlayer("Your player failed to sync. Please reconnect.");
            playerRequestCounts.remove(key);
        } else {
            LOGGER.warn("{} tried to request entity {}, which is the player {}! This means that server is missing that player.", connection.externalServer.getName(), this.uuid, serverPlayer.getScoreboardName());
            CompletableFuture.runAsync(() -> playerRequestCounts.remove(key), CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS));
        }
    }
}
