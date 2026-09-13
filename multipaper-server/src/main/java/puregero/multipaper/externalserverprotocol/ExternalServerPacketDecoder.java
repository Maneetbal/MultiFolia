package puregero.multipaper.externalserverprotocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.List;
import java.util.function.Function;

public class ExternalServerPacketDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> list) throws Exception {
        int i = byteBuf.readableBytes();
        if (i != 0) {
            RegistryFriendlyByteBuf friendlyByteBuf = new RegistryFriendlyByteBuf(new FriendlyByteBuf(byteBuf), PacketCodecHelper.REGISTRY_ACCESS);
            int packetId = friendlyByteBuf.readVarInt();
            Function<RegistryFriendlyByteBuf, ExternalServerPacket> deserializer = ExternalServerPacketSerializer.getDeserializer(packetId);
            list.add(deserializer.apply(friendlyByteBuf));
        }
    }
}
