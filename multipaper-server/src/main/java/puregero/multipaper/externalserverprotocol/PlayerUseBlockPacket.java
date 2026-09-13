package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerUseBlockPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final BlockHitResult result;

    public PlayerUseBlockPacket(UUID uuid, BlockHitResult blockHitResult) {
        this.uuid = uuid;
        this.result = blockHitResult;
    }

    public PlayerUseBlockPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.result = in.readBlockHitResult();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeBlockHitResult(this.result);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Tried to use a block {} with a non-existent player of uuid {}", this.result.getBlockPos(), this.uuid);
                return;
            }

            player.level().getBlockState(this.result.getBlockPos()).useWithoutItem(player.level(), player, this.result);
        });
    }
}
