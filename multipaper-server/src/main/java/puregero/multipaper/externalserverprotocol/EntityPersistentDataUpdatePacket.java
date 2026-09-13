package puregero.multipaper.externalserverprotocol;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.persistence.CraftPersistentDataContainer;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class EntityPersistentDataUpdatePacket extends ExternalServerPacket {
    private static final CompoundTag NULL_TAG = new CompoundTag();

    static {
        NULL_TAG.putBoolean("isNullTag", true);
    }

    public static boolean modifyingPersistentData = false;

    private final UUID world;
    private final UUID uuid;
    private final CompoundTag tag;

    public EntityPersistentDataUpdatePacket(Entity entity, CraftPersistentDataContainer container, Set<NamespacedKey> keys) {
        this.world = entity.level().getWorld().getUID();
        this.uuid = entity.getUUID();

        CompoundTag tag = new CompoundTag();
        for (NamespacedKey key : keys) {
            Tag value = container.getRaw().getOrDefault(key.toString(), NULL_TAG);
            tag.put(key.toString(), value);
        }

        this.tag = tag;
    }

    public EntityPersistentDataUpdatePacket(RegistryFriendlyByteBuf in) {
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
            modifyingPersistentData = true;
            World bukkitWorld = Bukkit.getWorld(this.world);

            if (bukkitWorld instanceof CraftWorld craftWorld) {
                ServerLevel level = craftWorld.getHandle();
                Entity entity = level.moonrise$getEntityLookup().getEntityIgnoringAccessible(this.uuid);
                if (entity != null) {
                    CraftEntity craftEntity = entity.getBukkitEntity();
                    for (Map.Entry<String, Tag> entry : this.tag.entrySet()) {
                        if (Objects.equals(entry.getValue(), NULL_TAG)) {
                            craftEntity.getPersistentDataContainer().remove(NamespacedKey.fromString(entry.getKey()));
                        } else {
                            craftEntity.getPersistentDataContainer().put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
            modifyingPersistentData = false;
        });
    }
}
