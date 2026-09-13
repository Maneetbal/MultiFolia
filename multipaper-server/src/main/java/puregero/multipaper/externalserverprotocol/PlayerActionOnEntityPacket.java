package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class PlayerActionOnEntityPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final UUID entityUuid;
    private final boolean hasInfiniteMaterials;
    private final Packet<? super ServerGamePacketListener> action;

    public PlayerActionOnEntityPacket(ServerPlayer player, Entity entity, Packet<ServerGamePacketListener> action) {
        this.uuid = player.getUUID();
        this.entityUuid = entity.getUUID();
        this.hasInfiniteMaterials = player.hasInfiniteMaterials();
        this.action = action;
    }

    public PlayerActionOnEntityPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.entityUuid = in.readUUID();
        this.hasInfiniteMaterials = in.readBoolean();
        this.action = PacketCodecHelper.decodeServerbound(in.readByteArray(), this.hasInfiniteMaterials);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeUUID(this.entityUuid);
        out.writeBoolean(this.hasInfiniteMaterials);
        out.writeByteArray(PacketCodecHelper.encodeServerbound(this.action, this.hasInfiniteMaterials));
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Tried to run an action on a non-existent player with uuid {}", this.uuid);
                return;
            }

            Entity entity = player.level().getEntityOrPart(this.entityUuid);

            if (entity == null) {
                LOGGER.warn("{} tried to run an action on a non-existent entity with uuid {}", player.getScoreboardName(), this.entityUuid);
                return;
            }

            Packet<ServerGamePacketListener> newPacket;

            // Refactor the entity id
            if (this.action instanceof ServerboundAttackPacket) {
                newPacket = new ServerboundAttackPacket(entity.getId());
            } else if (this.action instanceof ServerboundInteractPacket packet) {
                newPacket = new ServerboundInteractPacket(entity.getId(), packet.hand(), packet.location(), packet.usingSecondaryAction());
            } else {
                LOGGER.error("Unhandled action on entity {}", this.action);
                return;
            }

            newPacket.handle(player.connection);
        });
    }
}
