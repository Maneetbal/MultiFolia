package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PlayerChangeDimensionPacket extends ExternalServerPacket {

    private final UUID uuid;
    private final UUID world;
    private final double x;
    private final double y;
    private final double z;
    private final boolean reset;

    public PlayerChangeDimensionPacket(ServerPlayer player, boolean reset) {
        this.uuid = player.getUUID();
        this.world = player.level().getWorld().getUID();
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.reset = reset;
    }

    public PlayerChangeDimensionPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.world = in.readUUID();
        this.x = in.readDouble();
        this.y = in.readDouble();
        this.z = in.readDouble();
        this.reset = in.readBoolean();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeUUID(this.world);
        out.writeDouble(this.x);
        out.writeDouble(this.y);
        out.writeDouble(this.z);
        out.writeBoolean(this.reset);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            // Remove from an old world
            player.level().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);

            player.setPosRaw(this.x, this.y, this.z);

            if (this.reset) {
                player.keepLevel = false;
                player.newLevel = 0;
                player.newTotalExp = 0;
                player.expToDrop = 0;
                player.newExp = 0;

                player.reset();
            }

            // Add to a new world
            player.setServerLevel(level);
            player.unsetRemoved();
            level.addRespawnedPlayer(player);
        });
    }
}
