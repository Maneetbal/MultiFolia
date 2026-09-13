package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class AddItemToEntityContainerPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final UUID uuid;
    private final int slot;
    private final ItemStack itemStack;

    public AddItemToEntityContainerPacket(Entity entity, int slot, ItemStack itemStack) {
        this.world = entity.level().getWorld().getUID();
        this.uuid = entity.getUUID();
        this.slot = slot;
        this.itemStack = itemStack;
    }

    public AddItemToEntityContainerPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.slot = in.readByte();
        this.itemStack = ItemStack.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeByte(this.slot);
        ItemStack.STREAM_CODEC.encode(out, this.itemStack);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();
            Entity entity = level.getEntity(this.uuid);

            if (entity instanceof Container container) {
                // We can assume the item is being added from the side as it's cross servers, so any side direction such as north will do
                ItemStack leftOver = HopperBlockEntity.addItem(null, container, this.itemStack, Direction.NORTH);
                if (!leftOver.isEmpty()) {
                    LOGGER.warn("There was a left over {} after adding an item to {}", leftOver, container);
                    ItemEntity item = new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), leftOver);
                    level.addFreshEntity(item);
                }
            } else {
                LOGGER.warn("Tried to set a {} in slot {} in a non-existent entity in {} with uuid {}: {}", this.itemStack, this.slot, this.world, this.uuid, entity);
            }
        });
    }
}
