package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperRainHandler;

import java.util.UUID;

public class WeatherUpdatePacket extends ExternalServerPacket {
    private final UUID world;
    private final boolean raining;
    private final boolean thundering;
    private final int clearWeatherTime;
    private final int rainingTime;
    private final int thunderingTime;

    public WeatherUpdatePacket(UUID world, WeatherData weatherData) {
        this.world = world;
        this.raining = weatherData.isRaining();
        this.thundering = weatherData.isThundering();
        this.clearWeatherTime = weatherData.getClearWeatherTime();
        this.rainingTime = weatherData.getRainTime();
        this.thunderingTime = weatherData.getThunderTime();
    }

    public WeatherUpdatePacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.raining = in.readBoolean();
        this.thundering = in.readBoolean();
        this.clearWeatherTime = in.readInt();
        this.rainingTime = in.readInt();
        this.thunderingTime = in.readInt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeBoolean(this.raining);
        out.writeBoolean(this.thundering);
        out.writeInt(this.clearWeatherTime);
        out.writeInt(this.rainingTime);
        out.writeInt(this.thunderingTime);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World bukkitWorld = Bukkit.getWorld(this.world);
            if (bukkitWorld != null) {
                ServerLevel level = ((CraftWorld) bukkitWorld).getHandle();
                MultiPaperRainHandler.handle(level, this.raining, this.thundering, this.clearWeatherTime, this.rainingTime, this.thunderingTime);
            }
        });
    }
}
