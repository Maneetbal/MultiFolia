package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.handler.codec.compression.Zstd;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.mastermessagingprotocol.MessageLengthEncoder;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class HelloPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final String bungeecordName;
    private final long nanoTime;
    private final int supportedCompressionFlags;

    public HelloPacket(String bungeecordName, int supportedCompressionFlags) {
        this(bungeecordName, System.nanoTime(), supportedCompressionFlags);
    }

    public HelloPacket(String bungeecordName, long nanoTime, int supportedCompressionFlags) {
        this.bungeecordName = bungeecordName;
        this.nanoTime = nanoTime;
        this.supportedCompressionFlags = supportedCompressionFlags;
    }

    public HelloPacket(RegistryFriendlyByteBuf in) {
        this.bungeecordName = in.readUtf();
        this.nanoTime = in.readLong();
        this.supportedCompressionFlags = in.readVarInt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUtf(this.bungeecordName);
        out.writeLong(this.nanoTime);
        out.writeVarInt(this.supportedCompressionFlags);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        connection.externalServer = MultiPaper.getConnection().getOrCreateServer(this.bungeecordName);

        ExternalServerConnection previousConnection = connection.externalServer.getConnection();
        if (previousConnection == null || previousConnection.nanoTime < this.nanoTime) {
            LOGGER.info("Connected to external server {}", this.bungeecordName);
            initCompression(connection);
            connection.externalServer.setConnection(connection);
            onConnect(connection);
        } else if (previousConnection.nanoTime == this.nanoTime) {
            // How lucky do you have to be for nano time to be the same?
            LOGGER.info("A new connection to external server {} had the same nano time as a previous connection, resending with a new nano time...", this.bungeecordName);
            HelloPacket helloPacket = new HelloPacket(MultiPaperConfiguration.get().masterConnection.myName, connection.getSupportedCompressionFlags());
            if (helloPacket.nanoTime == this.nanoTime) {
                // Oh, nano time is broken, that's why
                throw new RuntimeException("System.nanoTime() does not work. (Returned " + helloPacket.nanoTime + ")");
            }
            connection.getChannel().writeAndFlush(helloPacket);
        } else {
            LOGGER.info("A new connection to external server {} failed as a previous connection had a newer nano time.", this.bungeecordName);
        }
    }

    private void initCompression(ExternalServerConnection connection) {
        int compressionType = 0;
        ChannelOutboundHandlerAdapter compressionHandler = null;

        if (MultiPaperConfiguration.get().peerConnection.compressionThreshold > 0 && !(connection.getChannel().remoteAddress() instanceof InetSocketAddress address && address.getAddress().isLoopbackAddress())) {
            if ((this.supportedCompressionFlags & SetCompressionPacket.ZSTD_COMPRESSION) == SetCompressionPacket.ZSTD_COMPRESSION && Zstd.isAvailable()) {
                compressionType = SetCompressionPacket.ZSTD_COMPRESSION;
                compressionHandler = SetCompressionPacket.createZstdCompressionEncoder();
            } else if ((this.supportedCompressionFlags & SetCompressionPacket.ZLIB_COMPRESSION) == SetCompressionPacket.ZLIB_COMPRESSION) {
                compressionType = SetCompressionPacket.ZLIB_COMPRESSION;
                compressionHandler = SetCompressionPacket.createZlibCompressionEncoder();
            }
        }

        if (compressionHandler != null) {
            LOGGER.info("Using compression {} with {}", compressionHandler.getClass().getSimpleName(), connection.externalServer.getName());
            ChannelOutboundHandlerAdapter finalCompressionHandler = compressionHandler;
            connection.getChannel().writeAndFlush(new SetCompressionPacket(compressionType)).addListener(future -> {
                if (MultiPaperConfiguration.get().peerConnection.consolidationDelay > 0) {
                    connection.getChannel().pipeline().addFirst("consolidator", new PacketConsolidationHandler());
                }

                connection.getChannel().pipeline()
                        .addFirst("compresser", finalCompressionHandler)
                        .addFirst("prepender", new MessageLengthEncoder());

                // Wait for the other side to initiate the compression
                CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() -> connection.onConnect.complete(null));
            });
        } else {
            connection.onConnect.complete(null);
        }
    }

    private void onConnect(ExternalServerConnection connection) {
        if (connection.nanoTime != this.nanoTime) {
            connection.nanoTime = this.nanoTime;
            connection.send(new HelloPacket(MultiPaperConfiguration.get().masterConnection.myName, this.nanoTime, connection.getSupportedCompressionFlags()));
        }

        MultiPaper.runSync(() -> {
            for (World world : Bukkit.getWorlds()) {
                connection.send(new SubscribeToWorldPacket(world.getUID()));
            }
        });

        if (MultiPaperConfiguration.get().syncSettings.syncScoreboards) {
            MultiPaper.runSync(() -> {
                // Only send scoreboards if we haven't just loaded
                if (MinecraftServer.getServer().getTickCount() > 5) {
                    sendScoreboard(MinecraftServer.getServer().getScoreboard(), connection);
                }
            });
        }
    }

    private void sendScoreboard(ServerScoreboard scoreboard, ExternalServerConnection connection) {
        Set<Objective> set = new HashSet<>();

        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            connection.send(new ScoreboardUpdatePacket(null, ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true)));
        }

        for (DisplaySlot slot : DisplaySlot.values()) {
            Objective scoreboardobjective = scoreboard.getDisplayObjective(slot);

            if (scoreboardobjective != null && !set.contains(scoreboardobjective)) {
                List<Packet<? super ClientGamePacketListener>> list = scoreboard.getStartTrackingPackets(scoreboardobjective);

                for (Packet<? super ClientGamePacketListener> value : list) {
                    connection.send(new ScoreboardUpdatePacket(null, scoreboardobjective.getCriteria(), value));
                }

                set.add(scoreboardobjective);
            }
        }
    }
}
