package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class DestroyBlockPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final BlockPos pos;

    public DestroyBlockPacket(UUID uuid, BlockPos pos) {
        this.uuid = uuid;
        this.pos = pos;
    }

    public DestroyBlockPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.pos = in.readBlockPos();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeBlockPos(this.pos);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("{} tried to break a block but they aren't online!", this.uuid);
                return;
            }

            player.gameMode.destroyBlock(this.pos);
        });
    }
}
