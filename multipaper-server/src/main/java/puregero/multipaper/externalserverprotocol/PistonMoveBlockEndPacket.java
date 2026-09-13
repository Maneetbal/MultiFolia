package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;
import puregero.multipaper.MultiPaperChunkHandler;

import java.util.UUID;

public class PistonMoveBlockEndPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    public static void finishBlockMove(ServerLevel level, BlockPos from, BlockPos to, Direction pistonDir, boolean extending, BlockState blockState, boolean isExternal) {
        LevelChunk chunk = level.getChunkIfLoaded(to);

        if (!MultiPaper.isChunkExternal(chunk)) {
            if (level.getBlockEntity(to) instanceof PistonMovingBlockEntity pistonMovingBlockEntity && pistonMovingBlockEntity.movedState.is(Blocks.AIR)) {
                pistonMovingBlockEntity.movedState = blockState;
            } else if (level.getBlockState(to).is(Blocks.AIR) || level.getBlockState(to).is(Blocks.PISTON_HEAD)) {
                BlockState movingPiston = Blocks.MOVING_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, pistonDir);
                MultiPaperChunkHandler.blockUpdateChunk = chunk; // Don't call preRemoveSideEffects and affectNeighborsAfterRemoval on the piston head
                level.setBlock(to, movingPiston, Block.UPDATE_NONE | Block.UPDATE_MOVE_BY_PISTON);
                MultiPaperChunkHandler.blockUpdateChunk = null;
                level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(to, movingPiston, blockState, pistonDir, extending, false));
            } else {
                LootParams.Builder lootParams = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(to)).withParameter(LootContextParams.TOOL, new ItemStack(Items.NETHERITE_PICKAXE)).withOptionalParameter(LootContextParams.BLOCK_ENTITY, null);
                blockState.getDrops(lootParams).forEach(item -> {
                    LOGGER.info("Dropping {} at {}", item, to);
                    ItemEntity entity = new ItemEntity(level, to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5, item);
                    entity.setDefaultPickUpDelay();
                    level.addFreshEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
                });
            }
            level.getChunkSource().blockChanged(to);

            if (isExternal) {
                chunk.moonrise$getChunkHolder().vanillaChunkHolder.broadcastChangesToOtherServers(chunk);
            }

            return;
        }

        // Ensure any changes have already been sent over before the server executes the piston move
        chunk.moonrise$getChunkHolder().vanillaChunkHolder.broadcastChangesToOtherServers(chunk);

        chunk.moonrise$getChunkHolder().externalOwner.getConnection().send(new PistonMoveBlockEndPacket(level, from, to, pistonDir, extending, blockState));
    }

    private final UUID world;
    private final BlockPos from;
    private final BlockPos to;
    private final Direction pistonDir;
    private final boolean extending;
    private final BlockState blockState;

    public PistonMoveBlockEndPacket(ServerLevel level, BlockPos from, BlockPos to, Direction pistonDir, boolean extending, BlockState blockState) {
        this.world = level.getWorld().getUID();
        this.from = from;
        this.to = to;
        this.pistonDir = pistonDir;
        this.extending = extending;
        this.blockState = blockState;
    }

    public PistonMoveBlockEndPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.from = in.readBlockPos();
        this.to = in.readBlockPos();
        this.pistonDir = in.readEnum(Direction.class);
        this.extending = in.readBoolean();
        this.blockState = Block.BLOCK_STATE_REGISTRY.byId(in.readInt());
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeBlockPos(this.from);
        out.writeBlockPos(this.to);
        out.writeEnum(this.pistonDir);
        out.writeBoolean(this.extending);
        out.writeInt(Block.BLOCK_STATE_REGISTRY.getId(this.blockState));
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World world = Bukkit.getWorld(this.world);

            if (!(world instanceof CraftWorld craftWorld)) {
                LOGGER.warn("{} tried to move a block in world {}, but we don't have that world loaded", connection.externalServer.getName(), this.world);
                return;
            }

            finishBlockMove(craftWorld.getHandle(), this.from, this.to, this.pistonDir, this.extending, this.blockState, true);
        });
    }
}
