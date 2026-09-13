package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperInventoryHandler;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.List;
import java.util.UUID;

public class PlayerInventoryUpdatePacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final String component;
    private final List<PacketCodecHelper.ItemWithSlotOpt> items;

    public PlayerInventoryUpdatePacket(UUID uuid, String component, List<PacketCodecHelper.ItemWithSlotOpt> items) {
        this.uuid = uuid;
        this.component = component;
        this.items = items;
    }

    public PlayerInventoryUpdatePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.component = in.readUtf();
        this.items = PacketCodecHelper.ItemWithSlotOpt.LIST_STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeUtf(this.component);
        PacketCodecHelper.ItemWithSlotOpt.LIST_STREAM_CODEC.encode(out, this.items);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player == null) {
                LOGGER.warn("Tried to update the inventory of a non-existent player uuid {}", this.uuid);
                return;
            }

            for (PacketCodecHelper.ItemWithSlotOpt entry : this.items) {
                MultiPaperInventoryHandler.updateInventory(player, this.component, entry.slot(), entry.replacing().orElse(null), entry.stack());
            }
            player.detectEquipmentUpdates();
        });
    }
}
