package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class AddItemToContainerPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final BlockPos pos;
    private final int slot;
    private final ItemStack itemStack;

    public AddItemToContainerPacket(BlockEntity blockEntity, int slot, ItemStack itemStack) {
        this(blockEntity.getLevel().getWorld().getUID(), blockEntity.getBlockPos(), slot, itemStack);
    }

    public AddItemToContainerPacket(UUID world, BlockPos blockPos, int slot, ItemStack itemStack) {
        this.world = world;
        this.pos = blockPos;
        this.slot = slot;
        this.itemStack = itemStack;
    }

    public AddItemToContainerPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.pos = in.readBlockPos();
        this.slot = in.readByte();
        this.itemStack = ItemStack.STREAM_CODEC.decode(in);
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeBlockPos(this.pos);
        out.writeByte(this.slot);
        ItemStack.STREAM_CODEC.encode(out, this.itemStack);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();
            Container container = HopperBlockEntity.getContainerAt(level, this.pos);

            if (container == null) {
                LOGGER.warn("Tried to set a {} in slot {} in a non-existent container at {} {}", this.itemStack, this.slot, this.world, this.pos);
            } else {
                // We can assume the item is being added from the side as it's cross-servers, so any side direction such as north will do
                ItemStack leftOver = HopperBlockEntity.addItem(null, container, itemStack, Direction.NORTH);
                if (!leftOver.isEmpty()) {
                    LOGGER.warn("There was a left over {} after adding an item to {}@{}{}", leftOver, container.getClass().getSimpleName(), this.world, this.pos);
                    ItemEntity item = new ItemEntity(level, this.pos.getX() + 0.5, this.pos.getY() + 1, this.pos.getZ() + 0.5, leftOver);
                    level.addFreshEntity(item);
                }
            }
        });
    }
}
