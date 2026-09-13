package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;

import java.util.UUID;

public class SetPlayerChatStatePacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID uuid;
    private final RemoteChatSession.Data chatSession;

    public SetPlayerChatStatePacket(UUID uuid, RemoteChatSession chatSession) {
        this.uuid = uuid;
        this.chatSession = chatSession.asData();
    }

    public SetPlayerChatStatePacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.chatSession = in.readNullable(RemoteChatSession.Data::read);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeNullable(this.chatSession, RemoteChatSession.Data::write);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);
        if (player != null) {
            player.setChatSession(new RemoteChatSession(
                    this.chatSession.sessionId(),
                    new ProfilePublicKey(this.chatSession.profilePublicKey())
            ));
        } else {
            LOGGER.warn("Could not find player for SetPlayerChatStatePacket {}", this.uuid);
        }
    }
}
