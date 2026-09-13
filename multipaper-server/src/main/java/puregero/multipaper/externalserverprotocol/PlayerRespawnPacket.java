package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.bukkit.event.player.PlayerRespawnEvent;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerRespawnPacket extends ExternalServerPacket {

    private final UUID uuid;
    private final boolean keepInventory;

    public PlayerRespawnPacket(UUID uuid, boolean keepInventory) {
        this.uuid = uuid;
        this.keepInventory = keepInventory;
    }

    public PlayerRespawnPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.keepInventory = in.readBoolean();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeBoolean(this.keepInventory);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            MinecraftServer server = MinecraftServer.getServer();
            PlayerList list = server.getPlayerList();

            list.respawn(
                    list.getPlayer(this.uuid), keepInventory,
                    Entity.RemovalReason.KILLED, PlayerRespawnEvent.RespawnReason.DEATH
            );
        });
    }
}
