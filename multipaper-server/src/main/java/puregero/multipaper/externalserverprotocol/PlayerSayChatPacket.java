package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerSayChatPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final String message;

    public PlayerSayChatPacket(UUID uuid, String message) {
        this.uuid = uuid;
        this.message = message;
    }

    public PlayerSayChatPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.message = in.readUtf();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeUtf(this.message);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Tried to run an action on a non-existent player with uuid {}", this.uuid);
                return;
            }

            player.getBukkitEntity().chat(this.message);
        });
    }
}
