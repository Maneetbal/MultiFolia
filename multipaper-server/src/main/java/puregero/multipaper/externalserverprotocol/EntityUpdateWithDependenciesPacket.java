package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperEntitiesHandler;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.*;

public class EntityUpdateWithDependenciesPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final UUID uuid;
    private final UUID[] uuids;
    private final Packet<? super ClientGamePacketListener> packet;
    private final ChunkPos chunkPos;

    public EntityUpdateWithDependenciesPacket(Entity entity, Collection<Entity> dependents, Packet<? super ClientGamePacketListener> packet) {
        this.world = entity.level().getWorld().getUID();
        this.uuid = entity.getUUID();
        this.uuids = dependents.stream().filter(Objects::nonNull).map(Entity::getUUID).toArray(UUID[]::new);
        this.packet = packet;
        this.chunkPos = entity.chunkPosition();
    }

    public EntityUpdateWithDependenciesPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.uuids = new UUID[in.readVarInt()];

        for (int i = 0; i < this.uuids.length; i++) {
            this.uuids[i] = in.readUUID();
        }

        this.packet = PacketCodecHelper.decodeClientbound(in.readByteArray());
        this.chunkPos = in.readChunkPos();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeVarInt(this.uuids.length);

        for (UUID uuid : this.uuids) {
            out.writeUUID(uuid);
        }

        out.writeByteArray(PacketCodecHelper.encodeClientbound(this.packet));
        out.writeChunkPos(this.chunkPos);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> handleLater(connection, 0));
    }

    private void handleLater(ExternalServerConnection connection, int depth) {
        World bukkitWorld = Bukkit.getWorld(this.world);

        if (bukkitWorld instanceof CraftWorld craftWorld) {
            ServerLevel level = craftWorld.getHandle();
            Entity entity = level.moonrise$getEntityLookup().getEntityIgnoringAccessible(this.uuid);
            if (entity == null) {
                if (EntityRemovePacket.removedEntities.containsKey(this.uuid)) {
                    return;
                }

                // If we can't find the entity, try again later,
                // the spawn entity packet is probably coming later

                // first we check if we really need to request the entity by checking if the entity's chunk is loaded
                if (!level.moonrise$getEntityLookup().isChunkLoaded(this.chunkPos)) {
                    return;
                }

                if (depth > 5) {
                    LOGGER.warn("Could not find entity {} for {}, requesting it", uuid, packet.getClass().getSimpleName());
                    RequestEntityPacket.requestEntity(connection, world, uuid);
                    return;
                }

                Bukkit.getScheduler().runTaskLater(MultiPaper.INTERNAL_PLUGIN, () -> handleLater(connection, depth + 1), 1);
                return;
            }

            Entity[] entities = new Entity[uuids.length];

            for (int i = 0; i < uuids.length; i++) {
                entities[i] = level.moonrise$getEntityLookup().getEntityIgnoringAccessible(uuids[i]);

                if (entities[i] == null) {
                    if (EntityRemovePacket.removedEntities.containsKey(uuids[i])) {
                        return;
                    }

                    if (depth > 5) {
                        LOGGER.warn("Could not find dependent entity {} for {}, requesting it", uuids[i], packet.getClass().getSimpleName());
                        RequestEntityPacket.requestEntity(connection, world, uuids[i]);
                        return;
                    }

                    Bukkit.getScheduler().runTaskLater(MultiPaper.INTERNAL_PLUGIN, () -> handleLater(connection, depth + 1), 1);
                    return;
                }
            }

            MultiPaperEntitiesHandler.handleEntityWithDependenicesUpdate(entity, entities, packet);
        }
    }

    public static void sendVehicleAndPassengersPacketsRecursivelyToServers(Entity entity, Collection<ExternalServer> servers) {
        if (!servers.isEmpty()) {
            compileVehicleAndPassengersPacketsRecursively(entity).forEach(packet -> MultiPaper.broadcastPacketToExternalServers(servers, packet));
        }
    }

    /**
     * Get a list of packets that contain the entity, and it's vehicles and passengers (recursively)'s NBT data, along with
     * any other packets that are required to link the passengers to their vehicles.
     * Order of packets must be maintained.
     */
    public static List<ExternalServerPacket> compileVehicleAndPassengersPacketsRecursively(Entity entity) {
        List<ExternalServerPacket> packets = new ArrayList<>();

        compilePacketsForEntityAndPassengers(entity.getRootVehicle(), packets);

        return packets;
    }

    private static void compilePacketsForEntityAndPassengers(Entity entity, List<ExternalServerPacket> packets) {
        if (!(entity instanceof ServerPlayer) && (entity.getVehicle() == null || entity.getVehicle() instanceof ServerPlayer)) {
            // This entity is the vehicle and will save the nbt for itself and all its passengers
            // Note that Players don't get saved, so any entity riding a player will also need to be saved
            packets.add(new EntityUpdateNBTPacket(entity));
        }

        for (Entity passenger : entity.getPassengers()) {
            compilePacketsForEntityAndPassengers(passenger, packets);
        }

        // Link the passengers to their vehicle (especially important if a vehicle or passenger is a player)
        // (And also unlink old passengers from the vehicle)
        packets.add(new EntityUpdateWithDependenciesPacket(entity, entity.getPassengers(), new ClientboundSetPassengersPacket(entity)));
    }
}
