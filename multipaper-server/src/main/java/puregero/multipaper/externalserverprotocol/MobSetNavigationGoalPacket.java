package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class MobSetNavigationGoalPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final UUID uuid;
    private final BlockPos goal;
    private final double speed;

    public MobSetNavigationGoalPacket(Mob mob, BlockPos goal) {
        this.world = mob.level().getWorld().getUID();
        this.uuid = mob.getUUID();
        this.goal = goal;
        this.speed = mob.getNavigation().speedModifier;
    }

    public MobSetNavigationGoalPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.goal = in.readBlockPos();
        this.speed = in.readDouble();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeBlockPos(this.goal);
        out.writeDouble(this.speed);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();

            if (!level.isLoaded(this.goal)) {
                // Chunk is not loaded, don't bother since this will force the chunk to be loaded in sync,
                // just find a new goal naturally
                return;
            }

            Entity entity = level.getEntity(this.uuid);
            if (entity instanceof Mob mob) {
                mob.getGoalSelector().getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(WrappedGoal::stop);
                mob.targetSelector.getAvailableGoals().stream().filter(WrappedGoal::isRunning).forEach(WrappedGoal::stop);
                mob.getNavigation().moveTo(mob.getNavigation().createPath(this.goal, 0), this.speed);
            } else {
                LOGGER.warn("Couldn't find mob {} for navigation goal", this.uuid);
            }
        });
    }
}
