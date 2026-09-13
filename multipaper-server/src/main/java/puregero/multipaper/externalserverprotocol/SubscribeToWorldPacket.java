package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class SubscribeToWorldPacket extends ExternalServerPacket {

    private final UUID world;

    public SubscribeToWorldPacket(UUID world) {
        this.world = world;
    }

    public SubscribeToWorldPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World bukkitWorld = Bukkit.getWorld(this.world);
            if (connection.subscribedWorlds.add(this.world) && bukkitWorld instanceof CraftWorld craftWorld) {
                onWorldSubscribe(connection, craftWorld);
            }
        });
    }

    private void onWorldSubscribe(ExternalServerConnection connection, CraftWorld craftWorld) {
        for (ServerPlayer player : craftWorld.getHandle().players()) {
            if (MultiPaper.isRealPlayer(player)) {
                PlayerCreatePacket.sendPlayer(player, connection);
            }
        }
    }
}
