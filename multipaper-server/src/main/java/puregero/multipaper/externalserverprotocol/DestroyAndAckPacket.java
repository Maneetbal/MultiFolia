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

public class DestroyAndAckPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final BlockPos pos;
    private final int sequence;
    private final String reason;

    public DestroyAndAckPacket(UUID uuid, BlockPos pos, int sequence, String reason) {
        this.uuid = uuid;
        this.pos = pos;
        this.sequence = sequence;
        this.reason = reason;
    }

    public DestroyAndAckPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.pos = in.readBlockPos();
        this.sequence = in.readVarInt();
        this.reason = in.readUtf();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeBlockPos(this.pos);
        out.writeVarInt(this.sequence);
        out.writeUtf(this.reason);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("{} tried to break a block but they aren't online!", this.uuid);
                return;
            }

            player.gameMode.destroyAndAck(this.pos, this.sequence, this.reason);
            player.destroyAndAckHandledByExternalServer = false;
            player.connection.ackBlockChangesUpTo(this.sequence);
        });
    }
}
