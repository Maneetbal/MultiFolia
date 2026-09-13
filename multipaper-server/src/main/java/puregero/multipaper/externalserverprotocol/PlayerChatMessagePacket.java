package puregero.multipaper.externalserverprotocol;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.util.PacketCodecHelper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlayerChatMessagePacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    public record MessagePayload(PlayerChatMessage message, ChatType.Bound boundChatType) {
    }

    private static final Map<MessagePayload, Set<UUID>> chatMessagesToFlush = new ConcurrentHashMap<>();
    private static CompletableFuture<Void> flushTask = CompletableFuture.completedFuture(null);

    public static void broadcastChatMessage(PlayerChatMessage message, ChatType.Bound boundChatType, UUID uuid) {
        // Buffer all uuids for a single chat message so that we aren't sending the entire message for every player

        chatMessagesToFlush.compute(new MessagePayload(message, boundChatType), (payload, uuids) -> {
            if (uuids == null) {
                uuids = new HashSet<>();
            }
            uuids.add(uuid);
            return uuids;
        });

        if (flushTask.isDone()) {
            flushTask = CompletableFuture.runAsync(PlayerChatMessagePacket::flush, CompletableFuture.delayedExecutor(1, TimeUnit.MILLISECONDS));
        }
    }

    public static void flush() {
        synchronized (chatMessagesToFlush) {
            // Synchronized to prevent concurrent flushes (concurrent additions are fine)
            Iterator<Map.Entry<MessagePayload, Set<UUID>>> iterator = chatMessagesToFlush.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<MessagePayload, Set<UUID>> entry = iterator.next();
                iterator.remove();
                MessagePayload payload = entry.getKey();
                PlayerChatMessage message = payload.message();
                ChatType.Bound boundChatType = payload.boundChatType();
                Set<UUID> uuids = entry.getValue();

                MultiPaper.broadcastPacketToExternalServers(new PlayerChatMessagePacket(message, boundChatType, uuids));
            }
        }
    }

    private final Collection<UUID> uuids;
    private final int index;
    private final UUID sender;
    private final UUID sessionId;
    private final MessageSignature messageSignature;
    private final String content;
    private final Instant timeStamp;
    private final long salt;
    private final List<MessageSignature> lastSeen;
    private final Component unsignedContent;
    private final FilterMask filterMask;
    private final ChatType.Bound boundChatType;

    public PlayerChatMessagePacket(PlayerChatMessage message, ChatType.Bound boundChatType, Set<UUID> uuids) {
        this.uuids = uuids;

        this.index = message.link().index();
        this.sender = message.link().sender();
        this.sessionId = message.link().sessionId();
        this.messageSignature = message.signature();
        this.content = message.signedBody().content();
        this.timeStamp = message.signedBody().timeStamp();
        this.salt = message.signedBody().salt();
        this.lastSeen = message.signedBody().lastSeen().entries();
        this.unsignedContent = message.unsignedContent();
        this.filterMask = message.filterMask();

        this.boundChatType = boundChatType;
    }

    public PlayerChatMessagePacket(RegistryFriendlyByteBuf in) {
        this.uuids = in.readCollection(Lists::newArrayListWithCapacity, PacketCodecHelper::readUUID);

        this.index = in.readInt();
        this.sender = in.readUUID();
        this.sessionId = in.readUUID();
        this.messageSignature = in.readNullable(MessageSignature::read);
        this.content = in.readUtf();
        this.timeStamp = in.readInstant();
        this.salt = in.readLong();
        this.lastSeen = in.readCollection(Lists::newArrayListWithCapacity, MessageSignature::read);
        this.unsignedContent = FriendlyByteBuf.readNullable(in, ComponentSerialization.TRUSTED_STREAM_CODEC);
        this.filterMask = FilterMask.read(in);

        this.boundChatType = ChatType.Bound.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeCollection(this.uuids, PacketCodecHelper::writeUUID);

        out.writeInt(this.index);
        out.writeUUID(this.sender);
        out.writeUUID(this.sessionId);
        out.writeNullable(this.messageSignature, MessageSignature::write);
        out.writeUtf(this.content);
        out.writeInstant(this.timeStamp);
        out.writeLong(this.salt);
        out.writeCollection(this.lastSeen, MessageSignature::write);
        FriendlyByteBuf.writeNullable(out, this.unsignedContent, ComponentSerialization.TRUSTED_STREAM_CODEC);
        FilterMask.write(out, this.filterMask);

        ChatType.Bound.STREAM_CODEC.encode(out, this.boundChatType);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        PlayerChatMessage chatMessage = new PlayerChatMessage(
                new SignedMessageLink(this.index, this.sender, this.sessionId),
                this.messageSignature,
                new SignedMessageBody(this.content, this.timeStamp, this.salt, new LastSeenMessages(this.lastSeen)),
                this.unsignedContent,
                this.filterMask
        );

        for (UUID uuid : this.uuids) {
            ServerPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                LOGGER.warn("Could not find player {}", uuid);
                continue;
            }

            if (MultiPaper.isRealPlayer(player)) {
                player.connection.sendPlayerChatMessage(chatMessage, this.boundChatType);
            }
        }
    }
}
