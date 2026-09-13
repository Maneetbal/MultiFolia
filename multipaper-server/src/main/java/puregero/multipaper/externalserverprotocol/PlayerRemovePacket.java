package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerKickEvent;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.event.player.PlayerLeaveExternalServerEvent;

import java.util.UUID;

public class PlayerRemovePacket extends ExternalServerPacket {

    public static final Component EXTERNAL_DISCONNECT_COMPONENT = Component.literal("Disconnected from external server");
    public static final Component LOGGED_IN_FROM_ANOTHER_LOCATION = Component.literal("Logged in from another location");
    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;

    public PlayerRemovePacket(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerRemovePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Tried to remove a non-existent player with uuid {}", this.uuid);
                return;
            }

            player.connection.disconnect(EXTERNAL_DISCONNECT_COMPONENT, PlayerKickEvent.Cause.TIMEOUT);
            PlayerLeaveExternalServerEvent playerLeaveExternalServerEvent = new PlayerLeaveExternalServerEvent(player.getGameProfile().id(), player.getGameProfile().name(), MultiPaperConfiguration.get().masterConnection.myName);
            Bukkit.getPluginManager().callEvent(playerLeaveExternalServerEvent);
        });
    }
}
