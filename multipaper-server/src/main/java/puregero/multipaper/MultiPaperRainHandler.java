package puregero.multipaper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;
import puregero.multipaper.externalserverprotocol.WeatherUpdatePacket;

import java.util.UUID;

public class MultiPaperRainHandler {
    private static boolean updatingWeather = false;

    public static void onWeatherChange(WeatherData weatherData, UUID world) {
        if (!updatingWeather) {
            // Run after all rain parameters have been set
            MultiPaper.runSync(() -> MultiPaper.broadcastPacketToExternalServers(new WeatherUpdatePacket(world, weatherData)));
        }
    }

    public static void handle(ServerLevel level, boolean raining, boolean thundering, int clearWeatherTime, int rainingTime, int thunderingTime) {
        updatingWeather = true;
        WeatherData weatherData = level.getWeatherData();
        weatherData.setRaining(raining);
        weatherData.setThundering(thundering);
        weatherData.setClearWeatherTime(clearWeatherTime);
        weatherData.setRainTime(rainingTime);
        weatherData.setThunderTime(thunderingTime);
        updatingWeather = false;
    }
}
