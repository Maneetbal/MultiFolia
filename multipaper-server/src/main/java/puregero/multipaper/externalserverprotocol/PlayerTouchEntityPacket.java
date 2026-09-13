package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerTouchEntityPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID player;
    private final UUID entity;

    public PlayerTouchEntityPacket(UUID player, UUID entity) {
        this.player = player;
        this.entity = entity;
    }

    public PlayerTouchEntityPacket(RegistryFriendlyByteBuf in) {
        this.player = in.readUUID();
        this.entity = in.readUUID();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.player);
        out.writeUUID(this.entity);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.player);

            if (player == null) {
                LOGGER.warn("Tried to run a touch entity on a non-existent player with uuid {}", this.player);
                return;
            }

            Entity entity = player.level().getEntity(this.entity);

            if (entity == null) {
                Entity.RemovalReason removalReason = EntityRemovePacket.removedEntities.get(this.entity);
                if (removalReason != null && removalReason.shouldDestroy()) {
                    connection.send(new EntityRemovePacket(player.level().getWorld().getUID(), this.entity, false));
                    return;
                }

                LOGGER.warn("{} tried to touch a non-existent entity with uuid {}, requesting it...", player.getScoreboardName(), this.entity);
                RequestEntityPacket.requestEntity(connection, player.level().uuid, this.entity);
                return;
            }

            entity.playerTouch(player);
        });
    }
}
