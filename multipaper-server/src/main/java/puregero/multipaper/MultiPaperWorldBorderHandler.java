package puregero.multipaper;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import puregero.multipaper.externalserverprotocol.SendUpdatePacket;

import java.util.UUID;

public class MultiPaperWorldBorderHandler implements BorderChangeListener {

    private static boolean updatingWorldBorder = false;

    @Override
    public void onSetSize(@NotNull WorldBorder border, double newSize) {
        this.onWorldBorderChange(border, new ClientboundSetBorderSizePacket(border));
    }

    @Override
    public void onLerpSize(@NotNull WorldBorder border, double fromSize, double targetSize, long ticks, long gameTime) {
        this.onWorldBorderChange(border, new ClientboundSetBorderLerpSizePacket(border));
    }

    @Override
    public void onSetCenter(@NotNull WorldBorder border, double x, double z) {
        this.onWorldBorderChange(border, new ClientboundSetBorderCenterPacket(border));
    }

    @Override
    public void onSetWarningTime(@NotNull WorldBorder border, int time) {
        this.onWorldBorderChange(border, new ClientboundSetBorderWarningDelayPacket(border));
    }

    @Override
    public void onSetWarningBlocks(@NotNull WorldBorder border, int blocks) {
        this.onWorldBorderChange(border, new ClientboundSetBorderWarningDistancePacket(border));
    }

    @Override
    public void onSetDamagePerBlock(@NotNull WorldBorder border, double damagePerBlock) {
    }

    @Override
    public void onSetSafeZone(@NotNull WorldBorder border, double safeZone) {
    }

    private void onWorldBorderChange(WorldBorder border, Packet<? super ClientGamePacketListener> packet) {
        if (updatingWorldBorder || border.world == null || border.world.getWorldBorder() != border) return;

        MultiPaper.broadcastPacketToExternalServers(border.world.uuid, new SendUpdatePacket(border.world.uuid, packet));
    }

    public static void handle(UUID world, Packet<?> packet) {
        ServerLevel level = ((CraftWorld) Bukkit.getWorld(world)).getHandle();

        updatingWorldBorder = true;

        if (packet instanceof ClientboundSetBorderSizePacket border) {
            level.getWorldBorder().setSize(border.getSize());
        } else if (packet instanceof ClientboundSetBorderLerpSizePacket border) {
            level.getWorldBorder().lerpSizeBetween(border.getOldSize(), border.getNewSize(), border.getLerpTime(), level.getGameTime());
        } else if (packet instanceof ClientboundSetBorderCenterPacket border) {
            level.getWorldBorder().setCenter(border.getNewCenterX(), border.getNewCenterZ());
        } else if (packet instanceof ClientboundSetBorderWarningDelayPacket border) {
            level.getWorldBorder().setWarningTime(border.getWarningDelay());
        } else if (packet instanceof ClientboundSetBorderWarningDistancePacket border) {
            level.getWorldBorder().setWarningBlocks(border.getWarningBlocks());
        }

        updatingWorldBorder = false;
    }
}
