package puregero.multipaper;

import net.minecraft.stats.Stat;
import puregero.multipaper.externalserverprotocol.PlayerStatsIncreasePacket;

import java.util.HashMap;
import java.util.UUID;

public class MultiPaperStatHandler {
    public static final HashMap<UUID, HashMap<Stat<?>, Integer>> statIncreases = new HashMap<>();

    public static void onStatIncrease(UUID uuid, Stat<?> stat, int value) {
        HashMap<Stat<?>, Integer> stats = statIncreases.computeIfAbsent(uuid, key -> new HashMap<>());

        int newValue = (int) Math.min((long) stats.getOrDefault(stat, 0) + (long) value, 2147483647L);
        stats.put(stat, newValue);
    }

    public static void sendIncreases() {
        statIncreases.forEach(
                (uuid, stats) -> MultiPaper.broadcastPacketToExternalServers(
                        new PlayerStatsIncreasePacket(uuid, stats)
                )
        );
        statIncreases.clear();
    }
}
