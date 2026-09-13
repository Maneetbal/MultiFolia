package puregero.multipaper.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PacketCodecHelper {
    public record ItemWithSlotOpt(int slot, ItemStack stack, Optional<ItemStack> replacing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, List<ItemWithSlotOpt>> LIST_STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ItemWithSlotOpt::slot,
                ItemStack.OPTIONAL_STREAM_CODEC, ItemWithSlotOpt::stack,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs::optional), ItemWithSlotOpt::replacing,
                ItemWithSlotOpt::new
        ).apply(ByteBufCodecs.list());
    }

    public static final RegistryAccess.Frozen REGISTRY_ACCESS = MinecraftServer.getServer().registryAccess();

    private static final ProtocolInfo<ClientGamePacketListener> CLIENTBOUND_PLAY_PROTOCOL = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(REGISTRY_ACCESS));
    private static final ProtocolInfo<ServerGamePacketListener> SERVERBOUND_PLAY_PROTOCOL_TRUE = GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(REGISTRY_ACCESS), () -> true);
    private static final ProtocolInfo<ServerGamePacketListener> SERVERBOUND_PLAY_PROTOCOL_FALSE = GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(REGISTRY_ACCESS), () -> false);

    public static Date readDate(ByteBuf buffer) {
        return new Date(buffer.readLong());
    }

    public static void writeDate(ByteBuf buffer, Date date) {
        buffer.writeLong(date.getTime());
    }

    public static UUID readUUID(ByteBuf buffer) {
        return FriendlyByteBuf.readUUID(buffer);
    }

    public static void writeUUID(ByteBuf buffer, UUID uuid) {
        FriendlyByteBuf.writeUUID(buffer, uuid);
    }

    public static BlockPos readBlockPos(ByteBuf buffer) {
        return FriendlyByteBuf.readBlockPos(buffer);
    }

    public static void writeBlockPos(ByteBuf buffer, BlockPos pos) {
        FriendlyByteBuf.writeBlockPos(buffer, pos);
    }

    private static ProtocolInfo<ClientGamePacketListener> getClientboundPlayProtocol() {
        return CLIENTBOUND_PLAY_PROTOCOL;
    }

    public static byte[] encodeClientbound(Packet<? super ClientGamePacketListener> packet) {
        ByteBuf buf = Unpooled.buffer();
        getClientboundPlayProtocol().codec().encode(buf, packet);

        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    public static Packet<? super ClientGamePacketListener> decodeClientbound(byte[] bytes) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        return getClientboundPlayProtocol().codec().decode(buf);
    }

    private static ProtocolInfo<ServerGamePacketListener> getServerboundPlayProtocol(boolean hasInfiniteMaterials) {
        return hasInfiniteMaterials ? SERVERBOUND_PLAY_PROTOCOL_TRUE : SERVERBOUND_PLAY_PROTOCOL_FALSE;
    }

    public static byte[] encodeServerbound(Packet<? super ServerGamePacketListener> packet, boolean hasInfiniteMaterials) {
        ByteBuf buf = Unpooled.buffer();
        getServerboundPlayProtocol(hasInfiniteMaterials).codec().encode(buf, packet);

        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    public static Packet<? super ServerGamePacketListener> decodeServerbound(byte[] bytes, boolean hasInfiniteMaterials) {
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        return getServerboundPlayProtocol(hasInfiniteMaterials).codec().decode(buf);
    }
}
