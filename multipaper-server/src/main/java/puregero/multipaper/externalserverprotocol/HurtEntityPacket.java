package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.event.entity.EntityDamageEvent;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.UUID;

public class HurtEntityPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final UUID uuid;
    private final float amount;
    private final boolean critical;
    private final boolean scissors;
    private final boolean stonecutter;
    private final UUID causingEntity;
    private final UUID directEntity;
    private final UUID eventEntity;
    private final BlockPos eventBlock;
    private final Vec3 sourcePosition;
    private final Holder<DamageType> typeHolder;
    private final EntityDamageEvent.DamageCause knownCause;

    public HurtEntityPacket(Entity entity, DamageSource source, float amount) {
        this.world = entity.level().getWorld().getUID();
        this.uuid = entity.getUUID();
        this.amount = amount;
        this.critical = source.isCritical();
        this.scissors = source.isScissors();
        this.stonecutter = source.isStonecutter();

        Entity causingEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();
        Entity eventEntity = source.eventEntityDamager();
        CraftBlock eventBlock = ((CraftBlock) source.eventBlockDamager());

        this.causingEntity = causingEntity == null ? null : causingEntity.getUUID();
        this.directEntity = directEntity == null ? null : directEntity.getUUID();
        this.eventEntity = eventEntity == null ? null : eventEntity.getUUID();
        this.eventBlock = eventBlock == null ? null : eventBlock.getPosition();
        this.sourcePosition = source.getSourcePosition();
        this.typeHolder = source.typeHolder();
        this.knownCause = source.knownCause();
    }

    public HurtEntityPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.amount = in.readFloat();
        this.critical = in.readBoolean();
        this.scissors = in.readBoolean();
        this.stonecutter = in.readBoolean();
        this.causingEntity = in.readNullable(PacketCodecHelper::readUUID);
        this.directEntity = in.readNullable(PacketCodecHelper::readUUID);
        this.eventEntity = in.readNullable(PacketCodecHelper::readUUID);
        this.eventBlock = in.readNullable(PacketCodecHelper::readBlockPos);
        this.sourcePosition = FriendlyByteBuf.readNullable(in, Vec3.STREAM_CODEC);
        this.typeHolder = FriendlyByteBuf.readNullable(in, DamageType.STREAM_CODEC);
        this.knownCause = in.readNullable(buf -> buf.readEnum(EntityDamageEvent.DamageCause.class));
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeFloat(this.amount);
        out.writeBoolean(this.critical);
        out.writeBoolean(this.scissors);
        out.writeBoolean(this.stonecutter);
        out.writeNullable(this.causingEntity, PacketCodecHelper::writeUUID);
        out.writeNullable(this.directEntity, PacketCodecHelper::writeUUID);
        out.writeNullable(this.eventEntity, PacketCodecHelper::writeUUID);
        out.writeNullable(this.eventBlock, PacketCodecHelper::writeBlockPos);
        FriendlyByteBuf.writeNullable(out, this.sourcePosition, Vec3.STREAM_CODEC);
        FriendlyByteBuf.writeNullable(out, this.typeHolder, DamageType.STREAM_CODEC);
        out.writeNullable(this.knownCause, FriendlyByteBuf::writeEnum);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();
            Entity entity = level.getEntity(this.uuid);

            if (entity == null) {
                LOGGER.warn("Could not find entity {}", this.uuid);
                return;
            }

            Entity causingEntity = getEntityOrLog(level, this.causingEntity, "entity");
            Entity directEntity = getEntityOrLog(level, this.directEntity, "direct entity");
            Entity eventEntity = getEntityOrLog(level, this.eventEntity, "event entity");

            DamageSource source = new DamageSource(
                    this.typeHolder,
                    directEntity,
                    causingEntity,
                    this.sourcePosition
            );

            if (this.critical) source = source.critical();
            if (this.scissors) source = source.scissors();
            if (this.stonecutter) source = source.stonecutter();

            if (eventEntity != null) source = source.eventEntityDamager(eventEntity);
            if (this.eventBlock != null) source = source.eventBlockDamager(level, this.eventBlock);
            if (this.knownCause != null) source = source.knownCause(this.knownCause);

            entity.hurtServer(level, source, this.amount);
        });
    }

    private static Entity getEntityOrLog(ServerLevel level, UUID uuid, String name) {
        if (uuid == null) return null;

        Entity entity = level.getEntity(uuid);
        if (entity == null) {
            LOGGER.warn("Unknown {} for damage source uid={}", name, uuid);
        }

        return entity;
    }
}
