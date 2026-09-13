package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.player.PlayerTeleportEvent;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.Set;
import java.util.UUID;

public class EntityTeleportPacket extends ExternalServerPacket {

    private final UUID entity;
    private final UUID level;
    private final UUID newLevel;
    private final Vec3 position;
    private final Vec3 deltaMovement;
    private final float yRot;
    private final float xRot;
    private final boolean missingRespawnBlock;
    private final boolean asPassenger;
    private final Set<Relative> relatives;
    private final PlayerTeleportEvent.TeleportCause cause;
    private final TeleportTransition.PassengerTeleportationMode passengerTeleportationMode;

    public EntityTeleportPacket(Entity entity, TeleportTransition transition) {
        this.entity = entity.getUUID();
        this.level = entity.level().getWorld().getUID();
        this.newLevel = transition.newLevel().uuid;
        this.position = transition.position();
        this.deltaMovement = transition.deltaMovement();
        this.yRot = transition.yRot();
        this.xRot = transition.xRot();
        this.missingRespawnBlock = transition.missingRespawnBlock();
        this.asPassenger = transition.asPassenger();
        this.relatives = transition.relatives();
        this.cause = transition.cause();
        this.passengerTeleportationMode = transition.passengerTeleportationMode();
    }

    public EntityTeleportPacket(RegistryFriendlyByteBuf in) {
        this.entity = in.readUUID();
        this.level = in.readUUID();
        this.newLevel = in.readUUID();
        this.position = Vec3.STREAM_CODEC.decode(in);
        this.deltaMovement = Vec3.STREAM_CODEC.decode(in);
        this.yRot = in.readFloat();
        this.xRot = in.readFloat();
        this.missingRespawnBlock = in.readBoolean();
        this.asPassenger = in.readBoolean();
        this.relatives = Relative.unpack(in.readInt());
        this.cause = in.readEnum(PlayerTeleportEvent.TeleportCause.class);
        this.passengerTeleportationMode = in.readEnum(TeleportTransition.PassengerTeleportationMode.class);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.entity);
        out.writeUUID(this.level);
        out.writeUUID(this.newLevel);
        Vec3.STREAM_CODEC.encode(out, this.position);
        Vec3.STREAM_CODEC.encode(out, this.deltaMovement);
        out.writeFloat(this.yRot);
        out.writeFloat(this.xRot);
        out.writeBoolean(this.missingRespawnBlock);
        out.writeBoolean(this.asPassenger);
        out.writeInt(Relative.pack(this.relatives));
        out.writeEnum(this.cause);
        out.writeEnum(this.passengerTeleportationMode);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.level)).getHandle();
            Entity entity = level.getEntity(this.entity);
            if (entity != null) {
                ChunkMap.TrackedEntity tracker = entity.moonrise$getTrackedEntity();
                if (tracker != null) {
                    tracker.serverEntity.teleportDelay = 10000;
                }

                entity.teleport(new TeleportTransition(
                        ((CraftWorld) Bukkit.getWorld(this.newLevel)).getHandle(),
                        this.position,
                        this.deltaMovement,
                        this.yRot,
                        this.xRot,
                        this.missingRespawnBlock,
                        this.asPassenger,
                        this.relatives,
                        TeleportTransition.DO_NOTHING,
                        this.cause,
                        this.passengerTeleportationMode
                ));
            }
        });
    }
}
