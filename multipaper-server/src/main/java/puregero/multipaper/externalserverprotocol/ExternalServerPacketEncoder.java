package puregero.multipaper.externalserverprotocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import puregero.multipaper.util.PacketCodecHelper;

public class ExternalServerPacketEncoder extends MessageToByteEncoder<ExternalServerPacket> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ExternalServerPacket msg, ByteBuf out) throws Exception {
        int packetId = ExternalServerPacketSerializer.getPacketId(msg);
        RegistryFriendlyByteBuf byteBuf = new RegistryFriendlyByteBuf(new FriendlyByteBuf(out), PacketCodecHelper.REGISTRY_ACCESS);
        byteBuf.writeVarInt(packetId);
        msg.write(byteBuf);
    }
}
