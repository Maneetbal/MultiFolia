package puregero.multipaper.externalserverprotocol;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueOutput;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerKickEvent;
import org.slf4j.Logger;
import puregero.multipaper.ExternalPlayer;
import puregero.multipaper.ExternalServer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.event.player.PlayerJoinExternalServerEvent;
import puregero.multipaper.util.PacketCodecHelper;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerCreatePacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final GameProfile gameProfile;
    private final ClientInformation clientInformation;
    private final UUID world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final GameType gamemode;
    private final byte[] ip;
    private final short port;
    private final CompoundTag saveData;
    private final byte[] advancements;
    private final byte[] stats;
    private final ConcurrentHashMap<String, String> data;
    private final ConcurrentHashMap<String, String> persistentData;
    private final int entityId;

    private PlayerCreatePacket(ServerPlayer player) {
        this.gameProfile = player.gameProfile;
        this.clientInformation = player.clientInformation();
        this.world = player.level().getWorld().getUID();
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.yaw = player.getYRot();
        this.pitch = player.getXRot();
        this.gamemode = player.gameMode.getGameModeForPlayer();
        this.ip = ((InetSocketAddress) player.connection.connection.address).getAddress().getAddress();
        this.port = (short) ((InetSocketAddress) player.connection.connection.address).getPort();
        try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(scopedCollector, PacketCodecHelper.REGISTRY_ACCESS);
            player.saveWithoutId(output);
            this.saveData = output.buildResult();
        }
        this.advancements = player.getAdvancements().toJson().toString().getBytes(StandardCharsets.UTF_8);
        this.stats = player.getStats().toJson().toString().getBytes(StandardCharsets.UTF_8);
        this.data = player.getBukkitEntity().data;
        this.persistentData = player.getBukkitEntity().persistentData;
        this.entityId = player.getId();
    }

    public PlayerCreatePacket(RegistryFriendlyByteBuf in) {
        in.maxNbtSize = Long.MAX_VALUE; // Allow unlimited NBT size
        this.gameProfile = ExtraCodecs.AUTHLIB_GAME_PROFILE.parse(NbtOps.INSTANCE, in.readNbt()).getOrThrow();
        this.clientInformation = new ClientInformation(in);
        this.world = in.readUUID();
        this.x = in.readDouble();
        this.y = in.readDouble();
        this.z = in.readDouble();
        this.yaw = in.readFloat();
        this.pitch = in.readFloat();
        this.gamemode = GameType.byId(in.readByte());
        this.ip = in.readByteArray();
        this.port = in.readShort();
        this.saveData = in.readNbt();

        this.advancements = in.readByteArray();
        this.stats = in.readByteArray();

        this.data = new ConcurrentHashMap<>();
        int dataLength = in.readInt();
        for (int i = 0; i < dataLength; i++) {
            this.data.put(in.readUtf(), in.readUtf());
        }

        this.persistentData = new ConcurrentHashMap<>();
        int persistentDataLength = in.readInt();
        for (int i = 0; i < persistentDataLength; i++) {
            this.persistentData.put(in.readUtf(), in.readUtf());
        }

        this.entityId = in.readVarInt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeNbt(ExtraCodecs.AUTHLIB_GAME_PROFILE.encode(this.gameProfile, NbtOps.INSTANCE, NbtOps.INSTANCE.empty()).getOrThrow());
        this.clientInformation.write(out);
        out.writeUUID(this.world);
        out.writeDouble(this.x);
        out.writeDouble(this.y);
        out.writeDouble(this.z);
        out.writeFloat(this.yaw);
        out.writeFloat(this.pitch);
        out.writeByte(this.gamemode.getId());
        out.writeByteArray(this.ip);
        out.writeShort(this.port);
        out.writeNbt(this.saveData);

        out.writeByteArray(this.advancements);
        out.writeByteArray(this.stats);

        Collection<Map.Entry<String, String>> dataEntries = new ArrayList<>(this.data.entrySet());
        out.writeInt(dataEntries.size());
        for (Map.Entry<String, String> entry : dataEntries) {
            out.writeUtf(entry.getKey());
            out.writeUtf(entry.getValue());
        }

        Collection<Map.Entry<String, String>> persistentDataEntries = new ArrayList<>(this.persistentData.entrySet());
        out.writeInt(persistentDataEntries.size());
        for (Map.Entry<String, String> entry : persistentDataEntries) {
            out.writeUtf(entry.getKey());
            out.writeUtf(entry.getValue());
        }

        out.writeVarInt(this.entityId);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        LOGGER.info("Adding player {} ({})", this.gameProfile.name(), this.gameProfile.id());
        MultiPaper.runSync(() -> {
            ServerPlayer existingPlayer = MinecraftServer.getServer().getPlayerList().getPlayer(this.gameProfile.id());
            if (existingPlayer != null) {
                LOGGER.warn("Trying to add external player {} ({}), but they're already online as a {}, kicking them", this.gameProfile.name(), this.gameProfile.id(), existingPlayer.getClass().getSimpleName());
                existingPlayer.connection.disconnect(PlayerRemovePacket.LOGGED_IN_FROM_ANOTHER_LOCATION, PlayerKickEvent.Cause.DUPLICATE_LOGIN);
            }

            InetSocketAddress address;
            try {
                address = new InetSocketAddress(InetAddress.getByAddress(this.ip), this.port & 0xFFFF);
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }

            ExternalPlayer player = ExternalPlayer.create(
                    connection, this.gameProfile, this.clientInformation,
                    this.world, this.x, this.y, this.z, this.yaw, this.pitch,
                    this.gamemode, address, this.saveData, this.advancements, this.stats, this.entityId
            );
            player.getBukkitEntity().data = this.data;
            player.getBukkitEntity().persistentData = this.persistentData;
            PlayerJoinExternalServerEvent playerJoinExternalServerEvent = new PlayerJoinExternalServerEvent(this.gameProfile.id(), this.gameProfile.name(), MultiPaperConfiguration.get().masterConnection.myName);
            Bukkit.getPluginManager().callEvent(playerJoinExternalServerEvent);
        });
    }

    private static void send(ExternalServerPacket packet, ExternalServerConnection... connections) {
        for (ExternalServerConnection connection : connections) {
            connection.send(packet);
        }
    }

    public static void sendPlayer(ServerPlayer player, ExternalServerConnection... connections) {
        if (connections.length == 0) {
            // Don't process packets if there's no one to send to
            return;
        }

        send(new PlayerCreatePacket(player), connections);
        send(new PlayerActionPacket(player, new ServerboundSetCarriedItemPacket(player.getInventory().selected)), connections);
        send(new EntityUpdatePacket(player, new ClientboundSetEntityDataPacket(player.getId(), player.getEntityData().packAll())), connections);
        send(new PlayerFoodUpdatePacket(player), connections);
        send(new PlayerListNameUpdatePacket(player), connections);
        send(new PlayerSetRespawnPosition(player), connections);

        send(new EntityUpdatePacket(player, new ClientboundSetEntityDataPacket(player.getId(), player.getEntityData().packAll())), connections);
        send(new PlayerActionPacket(player, new ServerboundClientInformationPacket(player.clientInformation())), connections);

        if (player.isPassenger() || player.isVehicle()) {
            NewChunkHolder newChunkHolder = MultiPaper.getChunkHolder(player.getRootVehicle());
            if (newChunkHolder != null) {
                List<ExternalServer> subscribedServers = Arrays.stream(connections).filter(e -> newChunkHolder.externalEntitiesSubscribers.contains(e.externalServer)).map(connection -> connection.externalServer).toList();
                EntityUpdateWithDependenciesPacket.sendVehicleAndPassengersPacketsRecursivelyToServers(player.getRootVehicle(), subscribedServers);
            }
        }
    }
}
