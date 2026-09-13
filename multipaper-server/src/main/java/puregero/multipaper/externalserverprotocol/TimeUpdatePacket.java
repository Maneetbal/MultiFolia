package puregero.multipaper.externalserverprotocol;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;

import java.util.Optional;
import java.util.UUID;

public class TimeUpdatePacket extends ExternalServerPacket {

    private final UUID world;
    private final long gameTime;
    private final long dayTime;
    private final boolean force;

    public TimeUpdatePacket(Level level, boolean force) {
        this.world = level.getWorld().getUID();
        this.gameTime = level.getGameTime();
        this.dayTime = level.clockManager().getTotalTicks(level.dimensionType().defaultClock().orElseThrow());
        this.force = force;
    }

    public TimeUpdatePacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.gameTime = in.readLong();
        this.dayTime = in.readLong();
        this.force = in.readBoolean();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeLong(this.gameTime);
        out.writeLong(this.dayTime);
        out.writeBoolean(this.force);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        World bukkitWorld = Bukkit.getWorld(this.world);
        if (bukkitWorld != null) {
            ServerLevel level = ((CraftWorld) bukkitWorld).getHandle();
            if (this.force || level.getGameTime() < this.gameTime - 20) {
                // We're more than a second behind, update us
                level.serverLevelData.setGameTime(this.gameTime);
                Optional<Holder<WorldClock>> defaultClock = level.dimensionType().defaultClock();
                defaultClock.ifPresent(clock -> level.clockManager().setTotalTicks(clock, this.dayTime));
            }
        }
    }
}
