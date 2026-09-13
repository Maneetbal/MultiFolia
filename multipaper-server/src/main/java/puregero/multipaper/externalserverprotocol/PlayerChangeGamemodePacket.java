package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerChangeGamemodePacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final GameType gamemode;

    public PlayerChangeGamemodePacket(ServerPlayer player) {
        this.uuid = player.getUUID();
        this.gamemode = player.gameMode.getGameModeForPlayer();
    }

    public PlayerChangeGamemodePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.gamemode = GameType.byId(in.readInt());
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeInt(this.gamemode.getId());
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Could not find player {}", this.uuid);
                return;
            }

            player.setGameMode(this.gamemode);
        });
    }
}
