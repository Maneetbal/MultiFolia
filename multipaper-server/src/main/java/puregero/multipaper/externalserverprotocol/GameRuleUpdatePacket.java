package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.World;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class GameRuleUpdatePacket extends ExternalServerPacket {

    private static boolean updatingGamerules = false;

    private final UUID world;
    private final String name;
    private final String value;

    public GameRuleUpdatePacket(UUID world, String name, String value) {
        this.world = world;
        this.name = name;
        this.value = value;
    }

    public GameRuleUpdatePacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.name = in.readUtf();
        this.value = in.readUtf();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUtf(this.name);
        out.writeUtf(this.value);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World bukkitWorld = Bukkit.getWorld(this.world);
            if (bukkitWorld != null) {
                updatingGamerules = true;
                bukkitWorld.setGameRuleValue(this.name, this.value);
                updatingGamerules = false;
            }
        });
    }

    public static void onGameRuleChange(UUID world, String name, String value) {
        if (!updatingGamerules) {
            MultiPaper.broadcastPacketToExternalServers(new GameRuleUpdatePacket(world, name, value));
        }
    }
}
