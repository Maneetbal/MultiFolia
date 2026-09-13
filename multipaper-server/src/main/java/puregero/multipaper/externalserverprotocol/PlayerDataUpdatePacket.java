package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerDataUpdatePacket extends ExternalServerPacket {

    private final UUID uuid;
    private final boolean persistent;
    private final String key;
    private final String value;

    public PlayerDataUpdatePacket(UUID uuid, boolean persistent, String key, String value) {
        this.uuid = uuid;
        this.persistent = persistent;
        this.key = key;
        this.value = value;
    }

    public PlayerDataUpdatePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.persistent = in.readBoolean();
        this.key = in.readUtf();
        this.value = in.readNullable(FriendlyByteBuf::readUtf);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeBoolean(this.persistent);
        out.writeUtf(this.key);
        out.writeNullable(this.value, FriendlyByteBuf::writeUtf);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player != null) {
                if (this.value != null) {
                    if (this.persistent) {
                        player.getBukkitEntity().persistentData.put(this.key, this.value);
                    } else {
                        player.getBukkitEntity().data.put(this.key, this.value);
                    }
                } else {
                    if (this.persistent) {
                        player.getBukkitEntity().persistentData.remove(this.key);
                    } else {
                        player.getBukkitEntity().data.remove(this.key);
                    }
                }
            }
        });
    }
}
