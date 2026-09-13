package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class DeopPlayerPacket extends ExternalServerPacket {

    private static boolean handlingPacket = false;
    private final String name;
    private final UUID uuid;

    public DeopPlayerPacket(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public DeopPlayerPacket(RegistryFriendlyByteBuf in) {
        this.name = in.readNullable(FriendlyByteBuf::readUtf);
        this.uuid = in.readNullable(PacketCodecHelper::readUUID);
    }

    public static void broadcast(String name, UUID uuid) {
        if (!handlingPacket && MultiPaperConfiguration.get().syncSettings.syncJsonFiles) {
            MultiPaper.broadcastPacketToExternalServers(new DeopPlayerPacket(name, uuid));
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
            Player player = Bukkit.getPlayer(this.uuid);
            if (player != null) {
                player.setOp(false);
            } else {
                MinecraftServer.getServer().getPlayerList().getOps().remove(new NameAndId(this.uuid, this.name));
            }
            handlingPacket = false;
        });
    }
}
