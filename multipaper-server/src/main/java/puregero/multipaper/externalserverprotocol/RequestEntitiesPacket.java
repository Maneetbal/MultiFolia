package puregero.multipaper.externalserverprotocol;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;

import java.util.ConcurrentModificationException;
import java.util.Set;

public class RequestEntitiesPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final String world;
    private final String path;
    private final int cx;
    private final int cz;

    public RequestEntitiesPacket(String world, String path, int cx, int cz) {
        this.world = world;
        this.path = path;
        this.cx = cx;
        this.cz = cz;
    }

    public RequestEntitiesPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUtf();
        this.path = in.readUtf();
        this.cx = in.readInt();
        this.cz = in.readInt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUtf(this.world);
        out.writeUtf(this.path);
        out.writeInt(this.cx);
        out.writeInt(this.cz);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        sendChunkLater(connection, this.world, this.path, this.cx, this.cz, 0);
    }

    private static void sendChunkLater(ExternalServerConnection connection, String world, String path, int cx, int cz, int depth) {
        ServerLevel level = ((CraftWorld) Bukkit.getWorld(world)).getHandle();
        NewChunkHolder chunkHolder = level.moonrise$getChunkTaskScheduler().chunkHolderManager.getChunkHolder(ChunkPos.pack(cx, cz));
        if (chunkHolder == null || chunkHolder.getEntityChunk() == null) {
            if (depth >= 20 || Bukkit.isStopping()) {
                LOGGER.warn("{} is requesting entities {},{},{} but we timed out waiting for them to load.", connection.externalServer.getName(), world, cx, cz);
                connection.send(new SendEntitiesPacket(world, path, cx, cz, null));
                return;
            }
            Bukkit.getScheduler().runTaskLater(MultiPaper.INTERNAL_PLUGIN, () -> sendChunkLater(connection, world, path, cx, cz, depth + 1), 1);
        } else {
            try {
                connection.send(new SendEntitiesPacket(level.getWorld().getName(), path, cx, cz, SendEntitiesPacket.getEntities(level, new ChunkPos(cx, cz), player -> {
                    if (MultiPaperConfiguration.get().optimizations.skipUnloadedPlayerMoves) {
                        // This player is now in a loaded chunk, ensure its position is up to date
                        connection.send(new EntityUpdatePacket(player, new ClientboundTeleportEntityPacket(player.getId(), PositionMoveRotation.of(player), Set.of(), player.onGround)));
                    }
                })));
            } catch (ConcurrentModificationException e) {
                LOGGER.warn("Got ConcurrentModificationException while sending entities, sending it in main thread instead");
                MultiPaper.runSync(() -> sendChunkLater(connection, world, path, cx, cz, depth));
            }
        }
    }
}
