package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerExperienceUpdatePacket extends ExternalServerPacket {
    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final float progress;
    private final int total;
    private final int level;

    public PlayerExperienceUpdatePacket(UUID uuid, float progress, int total, int level) {
        this.uuid = uuid;
        this.progress = progress;
        this.total = total;
        this.level = level;
    }

    public PlayerExperienceUpdatePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.progress = in.readFloat();
        this.total = in.readInt();
        this.level = in.readInt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeFloat(this.progress);
        out.writeInt(this.total);
        out.writeInt(this.level);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Could not find player {}", this.uuid);
                return;
            }

            player.experienceProgress = player.lastExperienceProgress = this.progress;
            player.totalExperience = player.lastTotalExperience = this.total;
            player.experienceLevel = player.lastExperienceLevel = this.level;
        });
    }
}
