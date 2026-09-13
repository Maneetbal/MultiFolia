package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class EntityUpdateNBTPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final UUID uuid;
    private final CompoundTag tag;

    public EntityUpdateNBTPacket(Entity entity) {
        this.world = entity.level().getWorld().getUID();
        this.uuid = entity.getUUID();

        try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(scopedCollector, entity.registryAccess());

            entity.isSyncing = true;
            entity.save(output);
            entity.isSyncing = false;

            if (output.isEmpty()) {
                throw new RuntimeException("Sending an empty entity " + entity);
            }

            this.tag = output.buildResult();
        }
    }

    public EntityUpdateNBTPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.tag = in.readNbt();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeNbt(this.tag);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World bukkitWorld = Bukkit.getWorld(this.world);

            if (bukkitWorld instanceof CraftWorld craftWorld) {
                try (final ProblemReporter.ScopedCollector scopedCollector = new ProblemReporter.ScopedCollector(LOGGER)) {
                    ServerLevel level = craftWorld.getHandle();
                    ValueInput input = TagValueInput.create(scopedCollector, level.registryAccess(), this.tag);
                    loadEntity(level, input, this.uuid);
                }
            }
        });
    }

    public static Entity loadEntity(ServerLevel level, ValueInput input, UUID uuid) {
        Entity.RemovalReason removalReason = EntityRemovePacket.removedEntities.get(uuid);
        if (removalReason != null && removalReason.shouldDestroy()) {
            // We've already removed this entity. This is likely a race condition, so don't recreate the entity.
            return null;
        }

        Entity entity = level.moonrise$getEntityLookup().getEntityIgnoringAccessible(uuid);

        if (entity == null) {
            entity = EntityType.loadEntityRecursive(input, level, EntitySpawnReason.LOAD, entity2 -> {
                if (level.moonrise$getEntityLookup().isChunkLoaded(entity2.chunkPosition())) {
                    level.moonrise$getEntityLookup().addNewEntity(entity2);
                    if (entity2 instanceof Mob mob) {
                        Leashable.LeashData leashData = mob.getLeashData();
                        if (leashData != null) {
                            Leashable.restoreLeashFromSave(mob, leashData);
                        }
                    }
                    return entity2;
                } else {
                    EntityRemovePacket.setEntityRemoved(uuid, Entity.RemovalReason.UNLOADED_TO_CHUNK, 20);
                    LOGGER.warn("Tried to create an entity from nbt, but the entities for that chunk aren't loaded: {}", entity2); // Warnings are there for a reason
                    return null;
                }
            });
        } else if (entity instanceof ServerPlayer player) {
            throw new RuntimeException("Tried to update the nbt of player " + player.getScoreboardName() + " to " + input);
        } else {
            entity.load(input);
            ChunkMap.TrackedEntity tracker = entity.moonrise$getTrackedEntity();
            if (tracker != null) {
                tracker.serverEntity.teleportDelay = 10000;
            }
        }

        Entity finalEntity = entity;
        input.childrenList("Passengers").ifPresent(list -> list.forEach(passengerTag -> {
            Entity passenger = loadEntity(level, passengerTag, passengerTag.read("UUID", UUIDUtil.CODEC).orElseThrow());

            if (passenger != null) {
                passenger.startRiding(finalEntity, true, true);
            }
        }));

        if (entity instanceof Mob mob) {
            Leashable.LeashData leashData = mob.getLeashData();
            if (leashData != null) {
                Leashable.restoreLeashFromSave(mob, leashData);
            }
        }

        return entity;
    }
}
