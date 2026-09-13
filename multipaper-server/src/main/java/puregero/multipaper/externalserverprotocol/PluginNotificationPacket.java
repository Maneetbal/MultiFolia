package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

public class PluginNotificationPacket extends ExternalServerPacket {
    private final String channel;
    private final byte[] data;

    public PluginNotificationPacket(String channel, byte[] data) {
        this.channel = channel;
        this.data = data;
    }

    public PluginNotificationPacket(RegistryFriendlyByteBuf in) {
        this.channel = in.readUtf();
        this.data = in.readByteArray();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUtf(this.channel);
        out.writeByteArray(this.data);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> ((CraftServer) Bukkit.getServer()).getMultiPaperNotificationManager().onNotification(connection, this.channel, this.data));
    }
}
