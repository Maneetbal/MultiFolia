package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class DifficultyUpdatePacket extends ExternalServerPacket {

    public static boolean updatingDifficulty = false;

    private final UUID world;
    private final Difficulty difficulty;

    public DifficultyUpdatePacket(ServerLevel level) {
        this.world = level.getWorld().getUID();
        this.difficulty = level.serverLevelData.getDifficulty();
    }

    public DifficultyUpdatePacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.difficulty = Difficulty.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        Difficulty.STREAM_CODEC.encode(out, this.difficulty);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World bukkitWorld = Bukkit.getWorld(this.world);
            if (bukkitWorld instanceof CraftWorld craftWorld) {
                updatingDifficulty = true;
                craftWorld.getHandle().getServer().setDifficulty(
                        craftWorld.getHandle(), this.difficulty, null, true
                );
                updatingDifficulty = false;
            }
        });
    }
}
