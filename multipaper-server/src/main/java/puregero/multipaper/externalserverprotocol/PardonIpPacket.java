package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;

public class PardonIpPacket extends ExternalServerPacket {

    private static boolean handlingPacket = false;
    private final String ip;

    public PardonIpPacket(String ip) {
        this.ip = ip;
    }

    public PardonIpPacket(RegistryFriendlyByteBuf in) {
        this.ip = in.readNullable(FriendlyByteBuf::readUtf);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeNullable(this.ip, FriendlyByteBuf::writeUtf);
    }

    public static void broadcast(String ip) {
        if (!handlingPacket && MultiPaperConfiguration.get().syncSettings.syncJsonFiles) {
            MultiPaper.broadcastPacketToExternalServers(new PardonIpPacket(ip));
        }
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            handlingPacket = true;
            MinecraftServer.getServer().getPlayerList().getIpBans().remove(this.ip);
            handlingPacket = false;
        });
    }
}
