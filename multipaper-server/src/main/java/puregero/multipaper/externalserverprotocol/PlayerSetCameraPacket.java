package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class PlayerSetCameraPacket extends ExternalServerPacket {

    public static boolean handlingSetCamera = false;

    private final UUID uuid;
    private final UUID uuidCamera;

    public PlayerSetCameraPacket(ServerPlayer player, Entity camera) {
        this.uuid = player.getUUID();
        this.uuidCamera = camera == null ? null : camera.getUUID();
    }

    public PlayerSetCameraPacket(RegistryFriendlyByteBuf in) {
        this.uuid = in.readUUID();
        this.uuidCamera = in.readNullable(PacketCodecHelper::readUUID);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.uuid);
        out.writeBoolean(this.uuidCamera != null);
        if (this.uuidCamera != null) {
            out.writeUUID(this.uuidCamera);
        }
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            handlingSetCamera = true;

            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(this.uuid);
            Entity entity = player.level().getEntity(this.uuidCamera);
            player.setCamera(entity);

            handlingSetCamera = false;
        });
    }
}
