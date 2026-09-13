package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperEffectsHandler;

import java.util.UUID;

public class EntityUpdateEffectPacket extends ExternalServerPacket {

    private final UUID world;
    private final UUID uuid;
    private final boolean remove;
    private final MobEffectInstance effect;

    public EntityUpdateEffectPacket(Entity entity, MobEffectInstance effect, boolean remove) {
        this.world = entity.level().getWorld().getUID();
        this.uuid = entity.getUUID();
        this.remove = remove;
        this.effect = effect;
    }

    public EntityUpdateEffectPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.remove = in.readBoolean();
        this.effect = MobEffectInstance.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeBoolean(this.remove);
        MobEffectInstance.STREAM_CODEC.encode(out, this.effect);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();
            Entity entity = level.moonrise$getEntityLookup().getEntityIgnoringAccessible(this.uuid);
            if (entity != null) {
                MultiPaperEffectsHandler.handle(entity, this.effect, this.remove);
            }
        });
    }
}
