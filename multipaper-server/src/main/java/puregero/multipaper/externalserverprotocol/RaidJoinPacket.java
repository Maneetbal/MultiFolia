package puregero.multipaper.externalserverprotocol;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import puregero.multipaper.ExternalServerConnection;
import puregero.multipaper.MultiPaper;

import java.util.UUID;

public class RaidJoinPacket extends ExternalServerPacket {
    private static boolean handlingJoin = false;

    private final UUID world;
    private final UUID uuid;
    private final int id;

    public RaidJoinPacket(Entity entity, int id) {
        this.world = entity.level().getWorld().getUID();
        this.uuid = entity.getUUID();
        this.id = id;
    }

    public RaidJoinPacket(RegistryFriendlyByteBuf in) {
        this.world = in.readUUID();
        this.uuid = in.readUUID();
        this.id = in.readInt();
    }

    public static void broadcastJoin(Raider entity, int id) {
        if (!handlingJoin) {
            MultiPaper.broadcastPacketToExternalServers(new RaidJoinPacket(entity, id));
        }
    }

    @Override
    public void write(RegistryFriendlyByteBuf out) {
        out.writeUUID(this.world);
        out.writeUUID(this.uuid);
        out.writeVarInt(this.id);
    }

    @Override
    public void handle(ExternalServerConnection connection) {
        MultiPaper.runSync(() -> {
            handlingJoin = true;
            World bukkitWorld = Bukkit.getWorld(this.world);

            if (bukkitWorld instanceof CraftWorld craftWorld) {
                ServerLevel level = craftWorld.getHandle();
                Entity entity = level.moonrise$getEntityLookup().getEntityIgnoringAccessible(this.uuid);
                Raid raid = level.getRaids().get(this.id);
                if (raid != null && entity instanceof Raider raider) {
                    raid.joinRaid(level, raid.getGroupsSpawned(), raider, null, true);
                }
            }
            handlingJoin = false;
        });
    }
}
