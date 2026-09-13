package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftWorld;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class SetEnderDragonFightPacket extends ExternalServerPacket {
    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final UUID world;
    private final EnderDragonFight enderDragonFight;

    public SetEnderDragonFightPacket(EnderDragonFight enderDragonFight) {
        this.world = enderDragonFight.level.uuid;
        this.enderDragonFight = enderDragonFight;
    }

    public SetEnderDragonFightPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.enderDragonFight = EnderDragonFight.CODEC.parse(NbtOps.INSTANCE, in.readNbt()).getOrThrow();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeNbt(EnderDragonFight.CODEC.encode(this.enderDragonFight, NbtOps.INSTANCE, NbtOps.INSTANCE.empty()).getOrThrow().asCompound().orElseThrow());
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            ServerLevel level = ((CraftWorld) Bukkit.getWorld(this.world)).getHandle();
            EnderDragonFight endDragonFight = level.getDragonFight();
            if (endDragonFight != null) {
                endDragonFight.setEnderDragonFight(this.enderDragonFight);
            }
        });
    }
}
