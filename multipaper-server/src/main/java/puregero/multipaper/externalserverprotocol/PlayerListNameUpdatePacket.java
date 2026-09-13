package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerListNameUpdatePacket extends ExternalServerPacket {

    private final UUID uuid;
    private final Component listName;

    public PlayerListNameUpdatePacket(ServerPlayer player) {
        this.uuid = player.getUUID();
        this.listName = player.listName;
    }

    public PlayerListNameUpdatePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.listName = ComponentSerialization.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        ComponentSerialization.STREAM_CODEC.encode(out, this.listName);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player != null) {
                player.listName = this.listName;

                for (ServerPlayer receiver : player.server.getPlayerList().getPlayers()) {
                    if (MultiPaper.isRealPlayer(receiver) && receiver.getBukkitEntity().canSee(player.getBukkitEntity())) {
                        receiver.connection.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
                    }
                }
            }
        });
    }
}
