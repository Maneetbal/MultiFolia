package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserWhiteListEntry;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class WhiteListPlayerPacket extends ExternalServerPacket {

    private static boolean handlingPacket = false;
    private final String name;
    private final UUID uuid;

    public WhiteListPlayerPacket(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public WhiteListPlayerPacket(RegistryFriendlyByteBuf in) {
        this.name = in.readNullable(FriendlyByteBuf::readUtf);
        this.uuid = in.readNullable(PacketCodecHelper::readUUID);
    }

    public static void broadcast(String name, UUID uuid) {
        if (!handlingPacket && MultiPaperConfiguration.get().syncSettings.syncJsonFiles) {
            MultiPaper.broadcastPacketToExternalServers(new WhiteListPlayerPacket(name, uuid));
        }
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeNullable(this.name, FriendlyByteBuf::writeUtf);
        out.writeNullable(this.uuid, PacketCodecHelper::writeUUID);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            handlingPacket = true;
            MinecraftServer.getServer().getPlayerList().getWhiteList().add(
                    new UserWhiteListEntry(new NameAndId(this.uuid, this.name))
            );
            handlingPacket = false;
        });
    }
}
