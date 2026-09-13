package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperEntitiesHandler;

import java.util.HashMap;
import java.util.UUID;

public class EntityRemovePacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    public static final HashMap<UUID, Entity.RemovalReason> removedEntities = new HashMap<>();

    private final UUID world;
    private final UUID uuid;
    private final boolean unloadedWithPlayer;

    public EntityRemovePacket(Entity entity, boolean unloadedWithPlayer) {
        this(entity.level().getWorld().getUID(), entity.getUUID(), unloadedWithPlayer);
    }

    public EntityRemovePacket(UUID world, UUID uuid, boolean unloadedWithPlayer) {
        this.world = world;
        this.uuid = uuid;
        this.unloadedWithPlayer = unloadedWithPlayer;
    }

    public EntityRemovePacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.unloadedWithPlayer = in.readBoolean();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeBoolean(this.unloadedWithPlayer);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(this::removeEntity);
    }

    private void removeEntity() {
        ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();

        Entity entity = level.moonrise$getEntityLookup().getEntityIgnoringAccessible(this.uuid);

        if (entity != null) {
            MultiPaperEntitiesHandler.removingEntity = true;
            Entity.RemovalReason reason = entity instanceof Player ? Entity.RemovalReason.KILLED : Entity.RemovalReason.DISCARDED;
            if (this.unloadedWithPlayer) reason = Entity.RemovalReason.UNLOADED_WITH_PLAYER;

            entity.setRemoved(reason);

            MultiPaperEntitiesHandler.removingEntity = false;
        } else if (!this.unloadedWithPlayer) {
            setEntityRemoved(this.uuid, Entity.RemovalReason.DISCARDED);
        }
    }

    public static void setEntityRemoved(UUID uuid, Entity.RemovalReason reason) {
        setEntityRemoved(uuid, reason, 300);
    }

    public static void setEntityRemoved(UUID uuid, Entity.RemovalReason reason, int durationInTicks) {
        if (reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) return;
        removedEntities.put(uuid, reason);
        Bukkit.getScheduler().runTaskLater(MultiPaper.INTERNAL_PLUGIN, () -> removedEntities.remove(uuid, reason), durationInTicks);
    }
}
