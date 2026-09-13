package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class PistonMoveBlockStartPacket extends ExternalServerPacket {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    public static void startBlockMove(ServerLevel level, BlockPos from, BlockPos to, Direction pistonDir, boolean extending, boolean isExternal) {
        LevelChunk fromChunk = level.getChunkIfLoaded(from);
        LevelChunk toChunk = level.getChunkIfLoaded(to);

        if (MultiPaper.isChunkExternal(fromChunk) && !MultiPaper.isChunkExternal(toChunk)) {
            // Place a moving piston head in our chunk so that it can start ticking and maintain proper timing
            BlockState movingPiston = Blocks.MOVING_PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, pistonDir);
            level.setBlock(to, movingPiston, Block.UPDATE_NONE | Block.UPDATE_MOVE_BY_PISTON);
            level.setBlockEntity(MovingPistonBlock.newMovingBlockEntity(to, movingPiston, Blocks.AIR.defaultBlockState(), pistonDir, extending, false));
            level.getChunkSource().blockChanged(to);
        }

        if (!MultiPaper.isChunkExternal(fromChunk)) {
            BlockState blockState = level.getBlockState(from);
            if (PistonBaseBlock.isPushable(blockState, level, from, extending ? pistonDir : pistonDir.getOpposite(), true, pistonDir)) {
                PistonMoveBlockEndPacket.finishBlockMove(level, from, to, pistonDir, extending, blockState, isExternal);
                level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_SKIP_ON_PLACE);
                level.getChunkSource().blockChanged(from);
            }

            if (isExternal) {
                fromChunk.moonrise$getChunkHolder().vanillaChunkHolder.broadcastChangesToOtherServers(fromChunk);
            }

            return;
        }

        // Ensure any changes have already been sent over before the server executes the piston move
        fromChunk.moonrise$getChunkHolder().vanillaChunkHolder.broadcastChangesToOtherServers(fromChunk);
        toChunk.moonrise$getChunkHolder().vanillaChunkHolder.broadcastChangesToOtherServers(toChunk);

        fromChunk.moonrise$getChunkHolder().externalOwner.getConnection().send(new PistonMoveBlockStartPacket(level, from, to, pistonDir, extending));
    }

    private final UUID world;
    private final BlockPos from;
    private final BlockPos to;
    private final Direction pistonDir;
    private final boolean extending;

    public PistonMoveBlockStartPacket(ServerLevel level, BlockPos from, BlockPos to, Direction pistonDir, boolean extending) {
        this.world = level.getWorld().getUID();
        this.from = from;
        this.to = to;
        this.pistonDir = pistonDir;
        this.extending = extending;
    }

    public PistonMoveBlockStartPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.from = in.readBlockPos();
        this.to = in.readBlockPos();
        this.pistonDir = in.readEnum(Direction.class);
        this.extending = in.readBoolean();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeBlockPos(this.from);
        out.writeBlockPos(this.to);
        out.writeEnum(this.pistonDir);
        out.writeBoolean(this.extending);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World world = Bukkit.getWorld(this.world);

            if (!(world instanceof CraftWorld craftWorld)) {
                LOGGER.warn("{} tried to move a block in world {}, but we don't have that world loaded", connection.externalServer.getName(), this.world);
                return;
            }

            startBlockMove(craftWorld.getHandle(), this.from, this.to, this.pistonDir, this.extending, true);
        });
    }
}
