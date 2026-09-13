package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerSetRespawnPosition extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static boolean settingRespawnPosition = false;

    private final UUID uuid;
    private final ServerPlayer.RespawnConfig config;

    public PlayerSetRespawnPosition(ServerPlayer player) {
        this.uuid = player.getUUID();
        this.config = player.getRespawnConfig();
    }

    public PlayerSetRespawnPosition(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.config = in.readNullable(buf -> ServerPlayer.RespawnConfig.CODEC.parse(NbtOps.INSTANCE, buf.readNbt()).getOrThrow());
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeNullable(this.config, (buf, cfg) -> buf.writeNbt(ServerPlayer.RespawnConfig.CODEC.encode(cfg, NbtOps.INSTANCE, NbtOps.INSTANCE.empty()).getOrThrow()));
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Tried to set a respawn position on a non-existent player with uuid {}", this.uuid);
                return;
            }

            settingRespawnPosition = true;
            player.setRespawnPosition(this.config, false);
            settingRespawnPosition = false;
        });
    }

    public static void broadcastRespawnPosition(ServerPlayer player) {
        if (!settingRespawnPosition) {
            MultiPaper.broadcastPacketToExternalServers(player, new PlayerSetRespawnPosition(player));
        }
    }
}
