package puregero.multipaper.externalserverprotocol;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalPlayer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperEntitiesHandler;

import java.util.UUID;

public class AddDeltaMovementPacket extends ExternalServerPacket {
    private static boolean handlingPacket = false;
    private final UUID world;
    private final UUID entity;
    private final Vec3 addend;

    public AddDeltaMovementPacket(UUID world, UUID entity, Vec3 addend) {
        this.world = world;
        this.entity = entity;
        this.addend = addend;
    }

    public static void broadcast(Entity entity, Vec3 addend) {
        if (!handlingPacket) {
            Entity controller = MultiPaperEntitiesHandler.getControllingPassenger(entity);
            if (controller instanceof ExternalPlayer externalPlayer) {
                externalPlayer.externalServerConnection.send(new AddDeltaMovementPacket(entity.level().getWorld().getUID(), entity.getUUID(), addend));
            } else {
                NewChunkHolder newChunkHolder = MultiPaper.getChunkHolder(entity);
                if (newChunkHolder != null && newChunkHolder.externalOwner != null && newChunkHolder.externalOwner.getConnection() != null) {
                    newChunkHolder.externalOwner.getConnection().send(new AddDeltaMovementPacket(entity.level().getWorld().getUID(), entity.getUUID(), addend));
                }
            }
        }
    }

    public AddDeltaMovementPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.entity = in.readUUID();
        this.addend = Vec3.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.entity);
        Vec3.STREAM_CODEC.encode(out, this.addend);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            handlingPacket = true;
            World world = Bukkit.getWorld(this.world);
            if (world instanceof CraftWorld craftWorld) {
                Entity entity = craftWorld.getHandle().getEntity(this.entity);
                if (entity != null) {
                    entity.addDeltaMovement(this.addend);
                }
            }
            handlingPacket = false;
        });
    }
}
