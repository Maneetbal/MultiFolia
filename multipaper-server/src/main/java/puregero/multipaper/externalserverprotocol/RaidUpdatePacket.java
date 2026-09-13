package puregero.multipaper.externalserverprotocol;

import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

public class RaidUpdatePacket extends ExternalServerPacket {
    private final Raid raid;

    public RaidUpdatePacket(Raid raid) {
        this.raid = raid;
    }

    public RaidUpdatePacket(RegistryFriendlyByteBuf in) {
        this.raid = Raid.MAP_CODEC.decoder().parse(NbtOps.INSTANCE, in.readNbt()).getOrThrow();
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeNbt(Raid.MAP_CODEC.encoder().encode(this.raid, NbtOps.INSTANCE, NbtOps.INSTANCE.empty()).getOrThrow());
    }

    public static void broadcastUpdate(Raid raid) {
        MultiPaper.broadcastPacketToExternalServers(new RaidUpdatePacket(raid));
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            World bukkitWorld = Bukkit.getWorld(this.raid.level);

            if (bukkitWorld instanceof CraftWorld craftWorld) {
                ServerLevel level = craftWorld.getHandle();

                Raid old = level.getRaids().get(this.raid.id);
                if (old == null) {
                    level.getRaids().raidMap.put(this.raid.id, this.raid);
                } else {
                    old.setRaid(this.raid);
                }
                level.getRaids().chunkToRaidIdMap.put(ChunkPos.pack(this.raid.getCenter()), this.raid.id);
            }
        });
    }
}
