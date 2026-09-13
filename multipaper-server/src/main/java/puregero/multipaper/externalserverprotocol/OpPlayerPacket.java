package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.ServerOpListEntry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class OpPlayerPacket extends ExternalServerPacket {

    private static boolean handlingPacket = false;
    private final String name;
    private final UUID uuid;
    private final int level;
    private final boolean bypassPlayerLimit;

    public OpPlayerPacket(String name, UUID uuid, int level, boolean bypassPlayerLimit) {
        this.name = name;
        this.uuid = uuid;
        this.level = level;
        this.bypassPlayerLimit = bypassPlayerLimit;
    }

    public OpPlayerPacket(RegistryFriendlyByteBuf in) {
        this.name = in.readNullable(FriendlyByteBuf::readUtf);
        this.uuid = in.readNullable(PacketCodecHelper::readUUID);
        this.level = in.readInt();
        this.bypassPlayerLimit = in.readBoolean();
    }

    public static void broadcast(String name, UUID uuid, int level, boolean bypassPlayerLimit) {
        if (!handlingPacket && MultiPaperConfiguration.get().syncSettings.syncJsonFiles) {
            MultiPaper.broadcastPacketToExternalServers(new OpPlayerPacket(name, uuid, level, bypassPlayerLimit));
        }
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeNullable(this.name, FriendlyByteBuf::writeUtf);
        out.writeNullable(this.uuid, PacketCodecHelper::writeUUID);
        out.writeInt(this.level);
        out.writeBoolean(this.bypassPlayerLimit);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            handlingPacket = true;
            Player player = Bukkit.getPlayer(this.uuid);
            if (player != null) {
                player.setOp(true);
            } else {
                MinecraftServer.getServer().getPlayerList().getOps().add(new ServerOpListEntry(
                        new NameAndId(this.uuid, this.name), LevelBasedPermissionSet.forLevel(PermissionLevel.byId(this.level)), this.bypassPlayerLimit
                ));
            }
            handlingPacket = false;
        });
    }
}
