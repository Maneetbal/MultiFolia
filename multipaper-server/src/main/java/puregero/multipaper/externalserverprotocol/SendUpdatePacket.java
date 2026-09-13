package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperChunkHandler;
import puregero.multipaper.MultiPaperWorldBorderHandler;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class SendUpdatePacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final Packet<? super ClientGamePacketListener> packet;

    public SendUpdatePacket(UUID world, Packet<? super ClientGamePacketListener> packet) {
        this.world = world;
        this.packet = packet;
    }

    public SendUpdatePacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.packet = PacketCodecHelper.decodeClientbound(in.readByteArray());
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeByteArray(PacketCodecHelper.encodeClientbound(this.packet));
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        if (this.packet instanceof ClientboundBlockUpdatePacket || this.packet instanceof ClientboundSectionBlocksUpdatePacket || this.packet instanceof ClientboundLightUpdatePacket || this.packet instanceof ClientboundBlockEntityDataPacket) {
            MultiPaper.runSync(() -> MultiPaperChunkHandler.handleBlockUpdate(this.world, this.packet, 0));
        } else if (this.packet instanceof ClientboundSetBorderSizePacket || this.packet instanceof ClientboundSetBorderLerpSizePacket || this.packet instanceof ClientboundSetBorderCenterPacket || this.packet instanceof ClientboundSetBorderWarningDelayPacket || this.packet instanceof ClientboundSetBorderWarningDistancePacket) {
            MultiPaper.runSync(() -> MultiPaperWorldBorderHandler.handle(this.world, this.packet));
        } else {
            LOGGER.warn("Unhandled update packet of type {}", this.packet.getClass().getSimpleName());
        }
    }
}
