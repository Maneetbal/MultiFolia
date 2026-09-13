package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

public class WhitelistTogglePacket extends ExternalServerPacket {

    public static boolean updatingWhitelistToggle = false;

    private final boolean whitelistEnabled;

    public WhitelistTogglePacket(boolean whitelistEnabled) {
        this.whitelistEnabled = whitelistEnabled;
    }

    public WhitelistTogglePacket(RegistryFriendlyByteBuf in) {
        this.whitelistEnabled = in.readBoolean();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeBoolean(this.whitelistEnabled);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            updatingWhitelistToggle = true;
            MinecraftServer.getServer().setUsingWhitelist(this.whitelistEnabled);
            updatingWhitelistToggle = false;
        });
    }
}
