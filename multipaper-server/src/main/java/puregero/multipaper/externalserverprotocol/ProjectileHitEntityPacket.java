package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class ProjectileHitEntityPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final UUID uuid;
    private final UUID entityUuid;
    private final Vec3 location;

    public ProjectileHitEntityPacket(Projectile projectile, Entity entity, Vec3 location) {
        this.world = projectile.level().getWorld().getUID();
        this.uuid = projectile.getUUID();
        this.entityUuid = entity.getUUID();
        this.location = location;
    }

    public ProjectileHitEntityPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.entityUuid = in.readUUID();
        this.location = Vec3.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeUUID(this.entityUuid);
        Vec3.STREAM_CODEC.encode(out, this.location);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World bukkitWorld = Bukkit.getWorld(this.world);

            if (bukkitWorld instanceof CraftWorld craftWorld) {
                ServerLevel level = craftWorld.getHandle();
                Projectile projectile = (Projectile) level.getEntity(this.uuid);
                Entity entity = level.getEntity(this.entityUuid);

                if (projectile == null) {
                    LOGGER.warn("Tried to hit an entity with a projectile, but the projectile {} is null", this.uuid);
                    return;
                }

                if (entity == null) {
                    LOGGER.warn("Tried to hit an entity with a projectile {}, but the entity {} is null", projectile, this.entityUuid);
                    return;
                }

                projectile.onHit0(new EntityHitResult(entity, this.location));
            }
        });
    }
}
