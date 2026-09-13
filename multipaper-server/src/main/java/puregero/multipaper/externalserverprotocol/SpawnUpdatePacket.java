package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class SpawnUpdatePacket extends ExternalServerPacket {

    public static boolean updatingSpawn = false;

    private final UUID world;
    private final LevelData.RespawnData respawnData;

    public SpawnUpdatePacket(ServerLevel level) {
        this.world = level.getWorld().getUID();
        this.respawnData = level.getRespawnData();
    }

    public SpawnUpdatePacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.respawnData = LevelData.RespawnData.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        LevelData.RespawnData.STREAM_CODEC.encode(out, this.respawnData);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World bukkitWorld = Bukkit.getWorld(this.world);
            if (bukkitWorld instanceof CraftWorld craftWorld) {
                ServerLevel level = craftWorld.getHandle();
                updatingSpawn = true;
                level.setRespawnData(this.respawnData);
                updatingSpawn = false;
            }
        });
    }
}
