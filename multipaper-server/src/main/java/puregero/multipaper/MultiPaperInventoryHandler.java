package puregero.multipaper;

import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import puregero.multipaper.externalserverprotocol.PlayerActionPacket;
import puregero.multipaper.externalserverprotocol.PlayerInventoryUpdatePacket;
import puregero.multipaper.util.PacketCodecHelper;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class MultiPaperInventoryHandler {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    public static boolean updatingInventory = false;
    private static final Set<NonNullListFilter<? extends ItemStack>> modifiedInventories = new LinkedHashSet<>();

    public static boolean handlePacketFromExternalServer(ExternalServer server, ServerPlayer player, Packet<? super ClientGamePacketListener> packet) {
        if (packet instanceof ClientboundOpenScreenPacket) {
            // An external server has requested to open a window on a player
            player.openContainer = server;
        } else if (packet instanceof ClientboundContainerClosePacket) {
            // An external server has requested to close the open window on a player
            if (player.openContainer == server) {
                player.openContainer = null;
            }
        } else if (packet instanceof ClientboundSetExperiencePacket setExperiencePacket) {
            // An external server is changing the player's experience level
            player.experienceLevel = setExperiencePacket.getExperienceLevel();
            player.experienceProgress = setExperiencePacket.getExperienceProgress();
            player.totalExperience = setExperiencePacket.getTotalExperience();
        } else if (packet instanceof ClientboundPlayerPositionPacket(
                int id, PositionMoveRotation change, Set<Relative> relatives
        )) {
            // An external server is teleporting the player
            player.connection.teleport(change, relatives);
            server.getConnection().send(new PlayerActionPacket(player, new ServerboundAcceptTeleportationPacket(id)));
            return true;
        }

        return false;
    }

    /**
     * Returns true if the even should be canceled
     */
    public static boolean handleInteractEvent(ServerPlayer player, ServerboundUseItemOnPacket packet) {
        NewChunkHolder newChunkHolder = MultiPaper.getChunkHolder(player.level(), packet.getHitResult().getBlockPos());
        ItemStack item = player.getItemInHand(packet.getHand());
        if (MultiPaper.isChunkExternal(newChunkHolder) && !(item.getItem() instanceof BucketItem)) {
            newChunkHolder.externalOwner.getConnection().send(new PlayerActionPacket(player, packet));
            return true;
        }

        return false;
    }

    /**
     * Returns true if the even should be canceled
     */
    public static boolean handleUseItemEvent(ServerPlayer player, ServerboundUseItemPacket packet) {
        NewChunkHolder newChunkHolder = MultiPaper.getChunkHolder(player.level(), player.blockPosition());
        ItemStack item = player.getItemInHand(packet.getHand());
        if (MultiPaper.isChunkExternal(newChunkHolder) && item.getItem() instanceof EnderEyeItem) {
            newChunkHolder.externalOwner.getConnection().send(new PlayerActionPacket(player, packet));
            return true;
        } else if (MultiPaper.isRealPlayer(player) && item.getItem() instanceof FishingRodItem) {
            MultiPaper.broadcastPacketToExternalServers(newChunkHolder.externalSubscribers, () -> new PlayerActionPacket(player, packet));
        }

        return false;
    }

    /**
     * Returns true if the even should be canceled
     */
    public static boolean handleContainerEvent(ServerPlayer player, Packet<ServerGamePacketListener> containerPacket) {
        if (player.openContainer != null) {
            player.openContainer.getConnection().send(new PlayerActionPacket(player, containerPacket));

            if (containerPacket instanceof ServerboundContainerClosePacket) {
                player.openContainer = null;
            }

            return true;
        }

        return false;
    }

    public static void updateInventory(ServerPlayer player, String name, int slot, ItemStack replacingItem, ItemStack item) {
        updatingInventory = true; // Don't let these changes mark the inventories as dirty
        EntityEquipment equipment = player.getInventory().equipment;
        NonNullListFilter<ItemStack> component = null;
        EquipmentSlot eqSlot = null;
        switch (name) {
            case "items" -> component = player.getInventory().items;
            case "equipments" -> eqSlot = EquipmentSlot.BY_ID.apply(slot);
            case "enderchest" -> {
                MultiPaperEnderChestHandler.updateInventory(player, slot, item);
                updatingInventory = false;
                return;
            }
            default -> throw new IllegalArgumentException("Unknown inventory component of " + name);
        }

        item.dirty = false;

        ItemStack lastItem;
        ItemStack currentItem;
        if (component != null) {
            lastItem = component.lastItems.get(slot);
            currentItem = component.get(slot);
        } else {
            lastItem = equipment.get(eqSlot);
            currentItem = equipment.get(eqSlot);
        }

        if (!ItemStack.matches(lastItem, currentItem) && !MultiPaper.isRealPlayer(player)) {
            // Our changes haven't been sent yet, send them
            if (component != null) broadcastComponentChanges(player, component);
        }

        if (MultiPaper.isRealPlayer(player) && replacingItem != null && !ItemStack.matches(replacingItem, currentItem)) {
            // The expected item doesn't match, a merge is required
            // to Resend the item afterward to sync the other servers
            item.dirty = true;
            if (component != null) component.isDirty = true;
            if (isSameItemSameTagIgnoringDurability(replacingItem, item)) {
                if (replacingItem.getCount() != item.getCount()) {
                    int countDiff = item.getCount() - replacingItem.getCount();
                    if (countDiff > 0) {
                        item.setCount(countDiff);
                        addItem(item, player);
                    } else {
                        LOGGER.warn("{}: An external server tried to remove {} items from {}, but that item is now a {}. Searching for duped items to remove...", player.getScoreboardName(), countDiff, replacingItem, currentItem);
                        item.setCount(-countDiff);
                        removeNearbyItems(player, item);
                    }
                } else if (replacingItem.getDamageValue() != item.getDamageValue()) {
                    int damageDiff = item.getDamageValue() - replacingItem.getDamageValue();
                    if (isSameItemSameTagIgnoringDurability(currentItem, item)) {
                        currentItem.setDamageValue(currentItem.getDamageValue() + damageDiff);
                    }
                } else {
                    LOGGER.warn("{}: Trying to merge the same item same tags, but neither the count nor the durability is different. {} and {} and {}", player.getScoreboardName(), item, replacingItem, currentItem);
                }
            } else {
                addItem(item, player);
                if (!replacingItem.isEmpty()) {
                    // The item has probably duplicated, try to find one of the copies and remove it
                    LOGGER.info("{}: An external server tried to replace {} with a {}, but that item is now a {}. Searching for duped items to remove...", player.getScoreboardName(), replacingItem, item, currentItem);
                    removeNearbyItems(player, replacingItem);
                }
            }
        } else {
            if (component != null) {
                component.set(slot, item);
                component.lastItems.set(slot, item.copy()); // We don't need to do this if it's our player
            } else {
                equipment.set(eqSlot, item);
            }
        }

        updatingInventory = false;
    }

    private static void addItem(ItemStack itemStack, ServerPlayer player) {
        if (player.isDeadOrDying() || !player.getInventory().add(itemStack)) {
            player.drop(itemStack, false);
        }
    }

    private static void removeNearbyItems(ServerPlayer player, ItemStack itemToRemove) {
        for (Runnable runnable : new Runnable[]{
                () -> removeFromContainerMenu(player, itemToRemove, player.containerMenu),
                () -> removeFromContainerMenu(player, itemToRemove, player.inventoryMenu),
                () -> removeFromInventory(player, itemToRemove),
                () -> removeFromItemEntities(player, itemToRemove),
        }) {
            runnable.run();

            if (itemToRemove.isEmpty()) {
                return;
            }
        }
    }

    private static void removeFromContainerMenu(ServerPlayer player, ItemStack itemToRemove, AbstractContainerMenu containerMenu) {
        removeItems(player, itemToRemove, containerMenu::getCarried, (item, count) -> {
            LOGGER.info("{}: Removing {}x of {} from cursor in open menu {}", player.getScoreboardName(), count, itemToRemove, containerMenu.getClass().getSimpleName());
            containerMenu.setCarried(item);
        });

        for (Slot slot : containerMenu.slots) {
            removeItems(player, itemToRemove, slot::getItem, (item, count) -> {
                LOGGER.info("{}: Removing {}x of {} from open menu {}", player.getScoreboardName(), count, itemToRemove, containerMenu.getClass().getSimpleName());
                slot.set(item);
            });
        }
    }

    private static void removeFromInventory(ServerPlayer player, ItemStack itemToRemove) {
        NonNullListFilter<ItemStack> items = player.getInventory().items;
        for (int slot = 0; slot < items.size(); slot++) {
            final int finalSlot = slot;
            removeItems(player, itemToRemove, () -> items.get(finalSlot), (item, count) -> {
                LOGGER.info("{}: Removing {}x of {} from inventory", player.getScoreboardName(), count, itemToRemove);
                items.set(finalSlot, item);
            });
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            removeItems(player, itemToRemove, () -> player.getInventory().equipment.get(slot), (item, count) -> {
                LOGGER.info("{}: Removing {}x of {} from equipment", player.getScoreboardName(), count, itemToRemove);
                player.getInventory().equipment.set(slot, item);
            });
        }
    }

    private static void removeFromItemEntities(ServerPlayer player, ItemStack itemToRemove) {
        for (ItemEntity entity : player.level().getEntitiesOfClass(ItemEntity.class, AABB.ofSize(player.position(), 16, 16, 16))) {
            removeItems(player, itemToRemove, entity::getItem, (item, count) -> {
                LOGGER.info("{}: Removing {}x of {} from item entity at {}", player.getScoreboardName(), count, itemToRemove, entity.position());
                entity.remove(Entity.RemovalReason.DISCARDED); // Remove the item entity to update the item inside
                if (!item.isEmpty()) {
                    entity.spawnAtLocation((ServerLevel) entity.level(), item, 0); // Respawn it if it has items left
                }
            });
        }
    }

    private static void removeItems(ServerPlayer player, ItemStack itemToRemove, Supplier<ItemStack> getter, BiConsumer<ItemStack, Integer> setter) {
        if (itemToRemove.isEmpty()) return;

        ItemStack item = getter.get();
        if (ItemStack.isSameItemSameComponents(item, itemToRemove)) {
            int itemCount = item.getCount();
            if (item.getCount() < itemToRemove.getCount()) {
                item.setCount(0);
                setter.accept(ItemStack.EMPTY, itemCount);
                itemToRemove.setCount(itemToRemove.getCount() - itemCount);
            } else {
                item.setCount(itemCount - itemToRemove.getCount());
                setter.accept(item, itemToRemove.getCount());
                itemToRemove.setCount(0);
            }
        }
    }

    private static boolean isSameItemSameTagIgnoringDurability(ItemStack left, ItemStack right) {
        return ItemStack.matchesIgnoringComponents(left, right, type -> type == DataComponents.DAMAGE);
    }

    /**
     * Returns true if the changes to the inventory component should be marked as dirty.
     */
    public static <E extends ItemStack> boolean markDirty(NonNullListFilter<E> inventoryComponent) {
        if (!TickThread.isTickThread()) {
            LOGGER.warn("Asynchronous inventory modification. This is unsafe and will eventually cause an issue.", new IllegalStateException("Async access"));
        }

        if (!updatingInventory) {
            modifiedInventories.add(inventoryComponent);
            return true;
        }

        return false;
    }

    /**
     * Runs at the end of a vanilla tick. I.e., any changes to the inventory made in the tick will instantly be updated
     * to other servers without a tick delay.
     */
    public static void tick() {
        for (NonNullListFilter<? extends ItemStack> inventoryComponent : modifiedInventories) {
            if (inventoryComponent.player instanceof ServerPlayer player) {
                broadcastComponentChanges(player, inventoryComponent);
            }
        }
        modifiedInventories.clear();
    }

    public static void broadcastEquipmentChanges(ServerPlayer player, Map<EquipmentSlot, ItemStack> replacingMap, Map<EquipmentSlot, ItemStack> equipmentMap) {
        if (updatingInventory) return;

        boolean real = MultiPaper.isRealPlayer(player);
        List<PacketCodecHelper.ItemWithSlotOpt> items = new ArrayList<>();

        for (Map.Entry<EquipmentSlot, ItemStack> entry : equipmentMap.entrySet()) {
            EquipmentSlot slot = entry.getKey();
            ItemStack itemStack = entry.getValue();

            if (slot == EquipmentSlot.MAINHAND) continue;

            itemStack.dirty = false;
            items.add(new PacketCodecHelper.ItemWithSlotOpt(
                    slot.getId(), itemStack,
                    real ? Optional.empty() : Optional.of(replacingMap.getOrDefault(slot, ItemStack.EMPTY))
            ));
        }

        if (items.isEmpty()) return;

        MultiPaper.broadcastPacketToExternalServers(player, new PlayerInventoryUpdatePacket(player.getUUID(), "equipments", items));
        player.detectEquipmentUpdates();
    }

    public static void broadcastComponentChanges(ServerPlayer player, NonNullListFilter<? extends ItemStack> inventoryComponent) {
        if (!inventoryComponent.isDirty) return;

        inventoryComponent.isDirty = false;
        boolean real = MultiPaper.isRealPlayer(player);
        List<PacketCodecHelper.ItemWithSlotOpt> items = new ArrayList<>();

        for (int i = 0; i < inventoryComponent.size(); i++) {
            if (inventoryComponent.dirty[i] || inventoryComponent.get(i).dirty) {

                items.add(new PacketCodecHelper.ItemWithSlotOpt(
                        i, inventoryComponent.get(i),
                        real ? Optional.empty() : Optional.of(inventoryComponent.lastItems.get(i))
                ));

                inventoryComponent.dirty[i] = false;
                inventoryComponent.get(i).dirty = false;
                inventoryComponent.lastItems.set(i, inventoryComponent.get(i).copy());
            }
        }

        if (items.isEmpty()) return;

        MultiPaper.broadcastPacketToExternalServers(player, new PlayerInventoryUpdatePacket(player.getUUID(), "items", items));
        player.detectEquipmentUpdates();
    }
}
