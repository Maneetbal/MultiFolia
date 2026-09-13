package puregero.multipaper;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import puregero.multipaper.config.MultiPaperConfiguration;
import puregero.multipaper.externalserverprotocol.EntityUpdatePacket;
import puregero.multipaper.externalserverprotocol.HurtEntityPacket;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ExternalPlayer extends ServerPlayer {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    public static HashMap<UUID, byte[]> loadedAdvancements = new HashMap<>();
    public static HashMap<UUID, byte[]> loadedStats = new HashMap<>();
    public ExternalServerConnection externalServerConnection;
    private final InetSocketAddress address;
    private boolean sendPackets = true;
    public boolean updatingData = false;

    public static ExternalPlayer create(ExternalServerConnection externalServerConnection, GameProfile gameProfile, ClientInformation clientInformation, UUID world, double x, double y, double z, float yaw, float pitch, GameType gamemode, InetSocketAddress address, CompoundTag saveData, byte[] advancements, byte[] stats, int entityId) {
        loadedAdvancements.put(gameProfile.id(), advancements);
        loadedStats.put(gameProfile.id(), stats);
        return new ExternalPlayer(externalServerConnection, gameProfile, clientInformation, world, x, y, z, yaw, pitch, gamemode, address, saveData, entityId);
    }

    public ExternalPlayer(ExternalServerConnection externalServerConnection, GameProfile gameProfile, ClientInformation clientInformation, UUID world, double x, double y, double z, float yaw, float pitch, GameType gamemode, InetSocketAddress address, CompoundTag saveData, int entityId) {
        super(((CraftServer) Bukkit.getServer()).getServer(), ((CraftWorld) Bukkit.getWorld(world)).getHandle(), gameProfile, clientInformation);

        this.externalServerConnection = externalServerConnection;
        this.address = address;

        try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
            MultiPaperInventoryHandler.updatingInventory = true;
            this.load(TagValueInput.create(scopedCollector, this.registryAccess(), saveData));
            MultiPaperInventoryHandler.updatingInventory = false;
        }

        if (MultiPaperConfiguration.get().syncSettings.syncEntityIds) {
            this.setId(entityId);
            if (MultiPaperConfiguration.get().syncSettings.persistentPlayerEntityIds) {
                // MultiPaper - persistent entity ids for players across servers
                persistentEntityIds.put(gameProfile.id(), getId());
            }
        }

        this.firstTick = false;
        this.isRealPlayer = true;
        this.valid = true;
        this.onGround = true;
        this.connection = new ServerGamePacketListenerImpl(server, new ExternalPlayerConnection(PacketFlow.CLIENTBOUND), this, CommonListenerCookie.createInitial(gameProfile, false));
        this.setPos(x, y, z);
        this.setYRot(yaw);
        this.setXRot(pitch);

        for (int i = 0; i < this.server.getPlayerList().getPlayers().size(); ++i) {
            ServerPlayer player = this.server.getPlayerList().getPlayers().get(i);

            if (!player.getBukkitEntity().canSee(this.getBukkitEntity())) {
                continue;
            }

            player.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(this)));
        }

        this.server.getPlayerList().addPlayer(this);
        this.level().addNewPlayer(this);
        this.sendPackets = false;
        this.containerMenu.transferTo(containerMenu, this.getBukkitEntity());
        this.initInventoryMenu();
        this.sendPackets = true;
        this.setGameMode(gamemode);
        this.detectEquipmentUpdates();
    }

    @Override
    public void tick() {
        // Don't tick
    }

    public class ExternalPlayerConnection extends net.minecraft.network.Connection {
        public ExternalPlayerConnection(PacketFlow side) {
            super(side);
            this.address = ExternalPlayer.this.address;
        }

        @Override
        public void setReadOnly() {
            // Do nothing
        }

        @Override
        public void flushChannel() {
            // Do nothing
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void send(@NotNull Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
            if (ExternalPlayer.this.sendPackets && !(packet instanceof ClientboundPlayerAbilitiesPacket)
                    && !(packet instanceof ClientboundSetPlayerTeamPacket)
                    && !(packet instanceof ClientboundCommandsPacket)
                    && !(packet instanceof ClientboundSetScorePacket)
                    && !(packet instanceof ClientboundResetScorePacket)
                    && !(packet instanceof ClientboundSetObjectivePacket)
                    && !(packet instanceof ClientboundSetDisplayObjectivePacket)
                    && !(packet instanceof ClientboundSetChunkCacheCenterPacket)
                    && !(packet instanceof ClientboundSetChunkCacheRadiusPacket)) {
//                LOGGER.info("Forwarding packet " + packet);
                ExternalPlayer.this.externalServerConnection.sendPacket(ExternalPlayer.this, (Packet<? super ClientGamePacketListener>) packet);
            } else {
//                LOGGER.info("Not sending packet " + packet.getClass().getSimpleName());
            }
        }
    }

    @Override
    public void applyEffectsFromBlocks() {
        super.applyEffectsFromBlocks();
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> data) {
        if (!this.updatingData) {
            MultiPaper.broadcastPacketToExternalServers(new EntityUpdatePacket(this,
                    new ClientboundSetEntityDataPacket(getId(), Collections.singletonList(getEntityData().getItem(data).value()))));
        }
    }

    @Override
    public boolean hurtServer(@NotNull ServerLevel level, @NotNull DamageSource damageSource, float amount) {
        this.externalServerConnection.send(new HurtEntityPacket(this, damageSource, amount));
        return true;
    }
}
