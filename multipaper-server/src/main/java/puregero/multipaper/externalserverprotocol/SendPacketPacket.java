package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperInventoryHandler;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.List;
import java.util.UUID;

public class SendPacketPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID[] uuids;
    private final Packet<? super ClientGamePacketListener> packet;

    public SendPacketPacket(List<? extends ServerPlayer> players, Packet<? super ClientGamePacketListener> packet) {
        this.uuids = new UUID[players.size()];
        for (int i = 0; i < players.size(); i++) {
            this.uuids[i] = players.get(i).getUUID();
        }
        this.packet = packet;
    }

    public SendPacketPacket(ServerPlayer player, Packet<? super ClientGamePacketListener> packet) {
        this.uuids = new UUID[1];
        this.uuids[0] = player.getUUID();
        this.packet = packet;
    }

    public SendPacketPacket(RegistryFriendlyByteBuf in) {
        this.uuids = new UUID[in.readInt()];
        for (int i = 0; i < this.uuids.length; i++) {
            this.uuids[i] = in.readUUID();
        }
        this.packet = PacketCodecHelper.decodeClientbound(in.readByteArray());
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeInt(this.uuids.length);
        for (UUID uuid : this.uuids) {
            out.writeUUID(uuid);
        }
        out.writeByteArray(PacketCodecHelper.encodeClientbound(this.packet));
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        if (this.packet instanceof ClientboundPlayerPositionPacket) {
            MultiPaper.runSync(() -> doHandle(connection));
        } else {
            doHandle(connection);
        }
    }

    public void doHandle(ExternalServerConnection connection) {
        for (UUID uuid : this.uuids) {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(uuid);

            if (player == null) {
                LOGGER.warn("Tried to send a packet to a non-existent player uuid {}", uuid);
                continue;
            }

            if (MultiPaperInventoryHandler.handlePacketFromExternalServer(connection.externalServer, player, this.packet)) {
                continue;
            }

            player.connection.send(this.packet);
        }
    }
}
