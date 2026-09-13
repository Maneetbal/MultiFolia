package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.Date;
import java.util.UUID;

public class BanPlayerPacket extends ExternalServerPacket {

    private static boolean handlingPacket = false;
    private final String name;
    private final UUID uuid;
    private final Date created;
    private final Date expires;
    private final String reason;
    private final String source;

    public BanPlayerPacket(String name, UUID uuid, Date created, Date expires, String reason, String source) {
        this.name = name;
        this.uuid = uuid;
        this.created = created;
        this.expires = expires;
        this.reason = reason;
        this.source = source;
    }

    public BanPlayerPacket(RegistryFriendlyByteBuf in) {
        this.name = in.readNullable(FriendlyByteBuf::readUtf);
        this.uuid = in.readNullable(PacketCodecHelper::readUUID);
        this.created = in.readNullable(PacketCodecHelper::readDate);
        this.expires = in.readNullable(PacketCodecHelper::readDate);
        this.reason = in.readNullable(FriendlyByteBuf::readUtf);
        this.source = in.readNullable(FriendlyByteBuf::readUtf);
    }

    public static void broadcast(String name, UUID uuid, Date created, Date expires, String reason, String source) {
        if (!handlingPacket && MultiPaperConfiguration.get().syncSettings.syncJsonFiles) {
            MultiPaper.broadcastPacketToExternalServers(new BanPlayerPacket(name, uuid, created, expires, reason, source));
        }
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeNullable(this.name, FriendlyByteBuf::writeUtf);
        out.writeNullable(this.uuid, PacketCodecHelper::writeUUID);
        out.writeNullable(this.created, PacketCodecHelper::writeDate);
        out.writeNullable(this.expires, PacketCodecHelper::writeDate);
        out.writeNullable(this.reason, FriendlyByteBuf::writeUtf);
        out.writeNullable(this.source, FriendlyByteBuf::writeUtf);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            handlingPacket = true;
            MinecraftServer.getServer().getPlayerList().getBans().add(new UserBanListEntry(
                    new NameAndId(this.uuid, this.name), this.created, this.source, this.expires, this.reason
            ));
            handlingPacket = false;
        });
    }
}
