package puregero.multipaper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import puregero.multipaper.externalserverprotocol.PlayerInventoryUpdatePacket;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MultiPaperEnderChestHandler {

    private static boolean broadcastChanges = true;

    private final ServerPlayer player;
    private ItemStack[] sentItems = new ItemStack[0];
    private BukkitTask isScheduled = null;

    public MultiPaperEnderChestHandler(ServerPlayer player) {
        this.player = player;
    }

    public void containerChanged(PlayerEnderChestContainer container) {
        if (container.getContainerSize() != this.sentItems.length) {
            this.sentItems = new ItemStack[container.getContainerSize()];
        }

        if (this.isScheduled == null && broadcastChanges && this.player.server.getPlayerList().getPlayer(this.player.getUUID()) == this.player) {
            // Wait till they join to broadcast changes
            this.isScheduled = Bukkit.getScheduler().runTaskLater(MultiPaper.INTERNAL_PLUGIN, () -> {
                this.isScheduled = null;
                this.containerChanged(container);
            }, 1);
            return;
        }

        this.isScheduled = null;

        List<PacketCodecHelper.ItemWithSlotOpt> items = new ArrayList<>();
        for (int i = 0; i < this.sentItems.length; i++) {
            ItemStack item = container.getItem(i);
            if (item.equals(this.sentItems[i])) continue;

            this.sentItems[i] = item.copy();
            if (broadcastChanges) {
                items.add(new PacketCodecHelper.ItemWithSlotOpt(i, item, Optional.empty()));
            }
        }

        if (items.isEmpty()) return;

        MultiPaper.broadcastPacketToExternalServers(new PlayerInventoryUpdatePacket(this.player.getUUID(), "enderchest", items));
    }

    public static void updateInventory(ServerPlayer player, int slot, ItemStack item) {
        broadcastChanges = false;
        player.getEnderChestInventory().setItem(slot, item);
        broadcastChanges = true;
    }
}
