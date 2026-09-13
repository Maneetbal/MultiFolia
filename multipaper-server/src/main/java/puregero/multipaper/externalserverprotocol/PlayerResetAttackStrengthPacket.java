package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerResetAttackStrengthPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static boolean settingAttackStrength = false;

    private final UUID uuid;

    public PlayerResetAttackStrengthPacket(UUID uuid) {
        this.uuid = uuid;
    }

    public PlayerResetAttackStrengthPacket(RegistryFriendlyByteBuf in) {
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
                LOGGER.warn("Tried to reset attack strength of a non-existent player with uuid {}", this.uuid);
                return;
            }

            settingAttackStrength = true;
            player.resetAttackStrengthTicker();
            settingAttackStrength = false;
        });
    }

    public static void broadcastResetAttackStrength(Player player) {
        if (!settingAttackStrength && player instanceof ServerPlayer serverPlayer) {
            MultiPaper.broadcastPacketToExternalServers(serverPlayer, new PlayerResetAttackStrengthPacket(player.getUUID()));
        }
    }
}
