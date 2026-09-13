package puregero.multipaper;

import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.haproxy.*;
import io.netty.util.internal.SystemPropertyUtil;
import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.connection.DisconnectionReason;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.handshake.ClientIntent;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.network.protocol.handshake.HandshakeProtocols;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.externalserverprotocol.*;
import puregero.multipaper.mastermessagingprotocol.MessageBootstrap;
import puregero.multipaper.mastermessagingprotocol.MessageLengthDecoder;
import puregero.multipaper.mastermessagingprotocol.MessageLengthEncoder;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ExternalServerConnection extends ChannelInitializer<SocketChannel> implements Closeable {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private Channel channel;
    public long nanoTime = 0;
    public ExternalServer externalServer = null;
    public final CompletableFuture<Void> onConnect = new CompletableFuture<>();
    public HashSet<UUID> subscribedWorlds = new HashSet<>();
    private static final Queue<List<ExternalPlayer>> externalPlayerListPool = new LinkedList<>();
    private final HashMap<Packet<? super ClientGamePacketListener>, List<ExternalPlayer>> packetsToSend = new LinkedHashMap<>();
    public final ConcurrentHashMap<ChunkKey, Consumer<CompoundTag>> chunkCallbacks = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<ChunkKey, Consumer<CompoundTag>> entitiesCallbacks = new ConcurrentHashMap<>();
    public long lastPacketSent = 0;
    public long lastPacketReceived = 0;

    public ExternalServerConnection() {

    }

    public ExternalServerConnection(Channel channel) {
        this.channel = channel;
        setupPipeline();
        this.channel.config().setAutoRead(true);
        nanoTime = System.nanoTime();
        this.channel.writeAndFlush(new HelloPacket(MultiPaperConfiguration.get().masterConnection.myName, nanoTime, getSupportedCompressionFlags()));
    }

    public int getSupportedCompressionFlags() {
        int supportedCompressionFlags = SetCompressionPacket.ZLIB_COMPRESSION;
        if (Zstd.isAvailable()) {
            supportedCompressionFlags |= SetCompressionPacket.ZSTD_COMPRESSION;
        }
        return supportedCompressionFlags;
    }

    @Override
    public void initChannel(SocketChannel channel) {
        this.channel = channel;
    }

    public void sendMinecraftHandshake(String address, String secret, int port) {
        if (GlobalConfiguration.get() == null) {
            // Paper config hasn't been loaded yet, try again 100ms later
            CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS).execute(() -> sendMinecraftHandshake(address, secret, port));
            return;
        }

        ChannelFuture future2 = channel.newSucceededFuture();

        if (GlobalConfiguration.get().proxies.proxyProtocol) {
            channel.pipeline().addLast("haproxy-encoder", HAProxyMessageEncoder.INSTANCE);
            future2 = channel.writeAndFlush(new HAProxyMessage(HAProxyProtocolVersion.V2, HAProxyCommand.LOCAL, HAProxyProxiedProtocol.UNKNOWN, null, null, 0, 0));
        }

        future2.addListener(future1 -> {
            channel.pipeline()
                    .addLast("prepender", new MessageLengthEncoder())
                    .addLast("encoder", new PacketEncoder<>(HandshakeProtocols.SERVERBOUND));
            channel.writeAndFlush(new ClientIntentionPacket(0, address + "\00" + secret, port, ClientIntent.STATUS))
                    .addListener(future -> {
                        if (future.isSuccess()) {
                            setupPipeline();
                        } else if (future.cause() != null) {
                            throw new RuntimeException(future.cause());
                        }
                    });

        });
    }

    public void setupPipeline() {
        // Lets yeet minecraft's networking out of here
        while (channel.pipeline().last() != null) {
            channel.pipeline().removeLast();
        }

        // And add our own
        if (!Boolean.getBoolean("Paper.disableFlushConsolidate"))
            channel.pipeline().addFirst(new io.netty.handler.flush.FlushConsolidationHandler());

        channel.pipeline()
                .addLast("packetsplitter", new MessageLengthDecoder())
                .addLast("decoder", new ExternalServerPacketDecoder())
                .addLast("packetprepender", new MessageLengthEncoder())
                .addLast("encoder", new ExternalServerPacketEncoder())
                .addLast("packet_handler", new ExternalServerPacketHandler(this));

        // And put it onto our own event loop
        if (MessageBootstrap.getEventLoopGroup() != channel.eventLoop().parent()) {
            if (SystemPropertyUtil.getBoolean("multipaper.netty.useOwnEventLoop", true)) {
                channel.deregister().addListener((ChannelFutureListener) future -> {
                    MessageBootstrap.getEventLoopGroup().register(future.channel()).sync();
                });
            } else {
                LOGGER.info("Using Minecraft's event loop");
            }
        }

        // And when this closes
        channel.closeFuture().addListener(future -> {
            if (future.isDone()) {
                for (ServerPlayer player : MinecraftServer.getServer().getPlayerList().getPlayers()) {
                    if (player instanceof ExternalPlayer && ((ExternalPlayer) player).externalServerConnection == this) {
                        MultiPaper.runSync(() -> player.connection.disconnectAsync(Component.literal("External server disconnected"), DisconnectionReason.UNKNOWN));
                    }
                }
            }
        });
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void send(ExternalServerPacket packet) {
        if (!channel.isOpen()) {
            LOGGER.error("Channel is closed for {}", externalServer.getName(), new Throwable());
        } else {
            onConnect.thenRun(() -> {
                if (channel.eventLoop().inEventLoop()) {
                    lastPacketSent = System.currentTimeMillis();
                    channel.writeAndFlush(packet);
                } else {
                    lastPacketSent = System.currentTimeMillis();
                    channel.eventLoop().execute(() -> channel.writeAndFlush(packet));
                }
            });
        }
    }

    public void tick() {
        // Send the packets after each vanilla tick
        synchronized (packetsToSend) {
            packetsToSend.forEach((packet, players) -> {
                send(new SendPacketPacket(players, packet));
                players.clear();
                externalPlayerListPool.add(players);
            });
            packetsToSend.clear();
        }
    }

    public void sendPacket(ExternalPlayer player, Packet<? super ClientGamePacketListener> packet) {
        // Combine all the players that the packet's being sent to together
        // so that the packet only needs to be sent to the external server
        // just once, not duplicated for each player
        synchronized (packetsToSend) {
            List<ExternalPlayer> players = packetsToSend.computeIfAbsent(packet, key -> {
                List<ExternalPlayer> list = externalPlayerListPool.poll();
                if (list == null) {
                    list = new ArrayList<>();
                }
                return list;
            });
            if (players.contains(player)) {
                // Duplicate packet with the same message, flush the old one to maintain sending order
                tick();
                sendPacket(player, packet);
                return;
            }
            players.add(player);
        }
    }

    public void requestChunk(String world, String path, int cx, int cz, Consumer<CompoundTag> callback) {
        if (callback != null) {
            if (chunkCallbacks.put(new ChunkKey(world, cx, cz), callback) != null) {
                LOGGER.warn("A chunk callback already existed for {}, {}, {} (new request is to {})", world, cx, cz, externalServer.getName());
                LOGGER.warn("Stats for {}: last packet sent={}ms ago; last packet received={}ms ago", externalServer.getName(), System.currentTimeMillis() - lastPacketSent, System.currentTimeMillis() - lastPacketReceived);
            }
        }

        RequestChunkPacket.blocker = externalServer;
        send(new RequestChunkPacket(world, path, cx, cz));
    }

    public void requestEntities(String world, String path, int cx, int cz, Consumer<CompoundTag> callback) {
        if (callback != null) {
            if (entitiesCallbacks.put(new ChunkKey(world, cx, cz), callback) != null) {
                LOGGER.warn("An entities callback already existed for {}, {}, {} (new request is to {})", world, cx, cz, externalServer.getName());
                LOGGER.warn("Stats for {}: last packet sent={}ms ago; last packet received={}ms ago", externalServer.getName(), System.currentTimeMillis() - lastPacketSent, System.currentTimeMillis() - lastPacketReceived);
            }
        }

        send(new RequestEntitiesPacket(world, path, cx, cz));
    }
}
