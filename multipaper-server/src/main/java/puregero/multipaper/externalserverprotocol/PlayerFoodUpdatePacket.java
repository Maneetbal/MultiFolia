package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerFoodUpdatePacket extends ExternalServerPacket {
    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final int foodLevel;
    private final float saturationLevel;

    public PlayerFoodUpdatePacket(ServerPlayer player) {
        this.uuid = player.getUUID();
        this.foodLevel = player.getFoodData().getFoodLevel();
        this.saturationLevel = player.getFoodData().getSaturationLevel();
    }

    public PlayerFoodUpdatePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.foodLevel = in.readShort();
        this.saturationLevel = in.readFloat();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeShort(this.foodLevel);
        out.writeFloat(this.saturationLevel);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Could not find player {}", this.uuid);
                return;
            }

            player.getFoodData().setFoodLevel(player, this.foodLevel, false);
            player.getFoodData().setSaturation(player, this.saturationLevel, false);
        });
    }
}
