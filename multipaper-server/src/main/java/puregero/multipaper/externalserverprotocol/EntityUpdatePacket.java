package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperEntitiesHandler;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class EntityUpdatePacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final UUID uuid;
    private final Packet<? super ClientGamePacketListener> packet;

    private final ChunkPos chunkPos;

    public EntityUpdatePacket(Entity entity, Packet<? super ClientGamePacketListener> packet) {
        this.world = entity.level().getWorld().getUID();
        this.uuid = entity.getUUID();
        this.packet = packet;
        this.chunkPos = entity.chunkPosition();
    }

    public EntityUpdatePacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.packet = PacketCodecHelper.decodeClientbound(in.readByteArray());
        this.chunkPos = in.readChunkPos();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
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
                    if (depth >= 20) {
                        LOGGER.warn("Could not find entity {} for {}, requesting it", this.uuid, this.packet.getClass().getSimpleName());
                    }
                    RequestEntityPacket.requestEntity(connection, this.world, this.uuid);
                    return;
                }

                Bukkit.getScheduler().runTaskLater(MultiPaper.INTERNAL_PLUGIN, () -> handleLater(connection, depth + 1), 1);
                return;
            }

            MultiPaperEntitiesHandler.handleEntityUpdate(entity, this.packet);
        }
    }
}
