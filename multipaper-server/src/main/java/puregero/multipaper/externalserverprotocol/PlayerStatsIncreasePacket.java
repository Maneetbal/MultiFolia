package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerStatsIncreasePacket extends ExternalServerPacket {
    private final UUID uuid;
    private final HashMap<Stat<?>, Integer> stats;

    public PlayerStatsIncreasePacket(UUID uuid, HashMap<Stat<?>, Integer> stats) {
        this.uuid = uuid;
        this.stats = stats;
    }

    public PlayerStatsIncreasePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();

        int length = in.readInt();
        this.stats = new HashMap<>();
        for (int i = 0; i < length; i++) {
            Stat<?> stat = Stat.STREAM_CODEC.decode(in);
            this.stats.put(stat, in.readInt());
        }
    }

    @Override
    public synchronized void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);

        Set<Map.Entry<Stat<?>, Integer>> entries = this.stats.entrySet();
        out.writeInt(entries.size());
        for (Map.Entry<Stat<?>, Integer> entry : entries) {
            Stat.STREAM_CODEC.encode(out, entry.getKey());
            out.writeInt(entry.getValue());
        }
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);

            if (player != null) {
                this.stats.forEach((stat, value) -> {
                    int newValue = (int) Math.min((long) player.getStats().getValue(stat) + (long) value, 2147483647L);
                    player.getStats().setValue(player, stat, newValue);
                });
            }

            this.stats.clear();
        });
    }
}
