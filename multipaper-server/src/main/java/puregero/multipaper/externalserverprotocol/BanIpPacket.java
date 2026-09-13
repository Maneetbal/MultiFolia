package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.IpBanListEntry;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.Date;

public class BanIpPacket extends ExternalServerPacket {

    private static boolean handlingPacket = false;
    private final String ip;
    private final Date created;
    private final Date expires;
    private final String reason;
    private final String source;

    public BanIpPacket(String ip, Date created, Date expires, String reason, String source) {
        this.ip = ip;
        this.created = created;
        this.expires = expires;
        this.reason = reason;
        this.source = source;
    }

    public BanIpPacket(RegistryFriendlyByteBuf in) {
        this.ip = in.readNullable(FriendlyByteBuf::readUtf);
        this.created = in.readNullable(PacketCodecHelper::readDate);
        this.expires = in.readNullable(PacketCodecHelper::readDate);
        this.reason = in.readNullable(FriendlyByteBuf::readUtf);
        this.source = in.readNullable(FriendlyByteBuf::readUtf);
    }

    public static void broadcast(String ip, Date created, Date expires, String reason, String source) {
        if (!handlingPacket && MultiPaperConfiguration.get().syncSettings.syncJsonFiles) {
            MultiPaper.broadcastPacketToExternalServers(new BanIpPacket(ip, created, expires, reason, source));
        }
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeNullable(this.ip, FriendlyByteBuf::writeUtf);
        out.writeNullable(this.created, PacketCodecHelper::writeDate);
        out.writeNullable(this.expires, PacketCodecHelper::writeDate);
        out.writeNullable(this.reason, FriendlyByteBuf::writeUtf);
        out.writeNullable(this.source, FriendlyByteBuf::writeUtf);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            handlingPacket = true;
            MinecraftServer.getServer().getPlayerList().getIpBans().add(
                    new IpBanListEntry(this.ip, this.created, this.source, this.expires, this.reason)
            );
            handlingPacket = false;
        });
    }
}
