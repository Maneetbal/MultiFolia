package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class AdvancementGrantProgressPacket extends ExternalServerPacket {

    private static boolean updatingAdvancements = false;

    private final UUID uuid;
    private final String criterion;
    private final Identifier advancement;

    public AdvancementGrantProgressPacket(UUID uuid, String criterion, Identifier advancement) {
        this.uuid = uuid;
        this.criterion = criterion;
        this.advancement = advancement;
    }

    public AdvancementGrantProgressPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.criterion = in.readUtf();
        this.advancement = in.readIdentifier();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeUtf(this.criterion);
        out.writeIdentifier(this.advancement);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            updatingAdvancements = true;
            MinecraftServer server = MinecraftServer.getServer();
            ServerPlayer player = server.getPlayerList().getPlayer(this.uuid);
            if (player != null) {
                player.getAdvancements().award(server.getAdvancements().get(this.advancement), this.criterion);
            }
            updatingAdvancements = false;
        });
    }

    public static void onAdvancementGrantProgress(UUID uuid, String criterion, Identifier advancement) {
        if (!updatingAdvancements) {
            MultiPaper.broadcastPacketToExternalServers(new AdvancementGrantProgressPacket(uuid, criterion, advancement));
        }
    }
}
