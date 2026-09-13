package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class PlayerActionPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final boolean hasInfiniteMaterials;
    private final Packet<? super ServerGamePacketListener> action;

    public PlayerActionPacket(ServerPlayer player, Packet<? super ServerGamePacketListener> action) {
        this.uuid = player.getUUID();
        this.hasInfiniteMaterials = player.hasInfiniteMaterials();
        this.action = action;
    }

    public PlayerActionPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.hasInfiniteMaterials = in.readBoolean();
        this.action = PacketCodecHelper.decodeServerbound(in.readByteArray(), this.hasInfiniteMaterials);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
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

            if (this.action == null) {
                LOGGER.warn("Received a null action packet for player with uuid {}", this.uuid);
                return;
            }

            this.action.handle(player.connection);
        });
    }
}
