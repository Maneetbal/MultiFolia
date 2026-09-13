package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PullItemFromContainerPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final BlockPos source;
    private final int slot;
    private final ItemStack itemStack;
    private final BlockPos destination;

    public PullItemFromContainerPacket(BlockEntity blockEntity, int slot, ItemStack itemStack, Hopper destinationHopper) {
        this.world = blockEntity.getLevel().getWorld().getUID();
        this.source = blockEntity.getBlockPos();
        this.slot = slot;
        this.itemStack = itemStack;
        this.destination = new BlockPos((int) destinationHopper.getLevelX(), (int) destinationHopper.getLevelY(), (int) destinationHopper.getLevelZ());
    }

    public PullItemFromContainerPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.source = in.readBlockPos();
        this.slot = in.readInt();
        this.itemStack = ItemStack.STREAM_CODEC.decode(in);
        this.destination = in.readBlockPos();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeBlockPos(this.source);
        out.writeInt(this.slot);
        ItemStack.STREAM_CODEC.encode(out, this.itemStack);
        out.writeBlockPos(this.destination);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();
            Container container = HopperBlockEntity.getContainerAt(level, this.source);

            if (container == null) {
                LOGGER.warn("Tried to take a {} in slot {} in a non-existent container at {} {}", this.itemStack, this.slot, this.world, this.source);
            } else if (!ItemStack.isSameItemSameComponents(this.itemStack, container.getItem(this.slot))) {
                LOGGER.warn("Tried to take a {} in slot {} from container at {} {}, but it is a {}", this.itemStack, this.slot, this.world, this.source, container.getItem(this.slot));
            } else {
                ItemStack origItemStack = container.getItem(this.slot);
                int count = Math.min(this.itemStack.getCount(), origItemStack.getCount());
                ItemStack pulledItemStack = origItemStack.copy(true);
                pulledItemStack.setCount(count);
                origItemStack.setCount(origItemStack.getCount() - count);

                HopperBlockEntity.ignoreBlockEntityUpdates = true;
                container.setItem(this.slot, origItemStack);
                HopperBlockEntity.ignoreBlockEntityUpdates = false;
                container.setChanged();

                connection.send(new AddItemToContainerPacket(level.uuid, this.destination, 0, pulledItemStack));
            }
        });
    }
}
