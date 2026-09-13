package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import puregero.multipaper.ExternalServerConnection;

public abstract class ExternalServerPacket {

    public abstract void handle(ExternalServerConnection connection);

    public abstract void write(RegistryFriendlyByteBuf out);

}
