package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.WriteChunkMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.BooleanMessageReply;
import puregero.multipaper.server.LockManager;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.util.RegionFileCache;
import se.llbit.nbt.CompoundTag;
import se.llbit.nbt.ListTag;
import se.llbit.nbt.SpecificTag;

import java.io.*;

public class WriteChunkHandler {
    public static void handle(ServerConnection connection, WriteChunkMessage message) {
        if (message.isTransientEntities) {
            handleTransientEntities(connection, message);
            return;
        }

        writeData(connection, message, message.data);
    }

    private static void writeData(ServerConnection connection, WriteChunkMessage message, byte[] data) {
        LockManager manager = LockManager.getLockManager(message.type);

        RegionFileCache.putChunkDeflatedDataAsync(message.path, message.cx, message.cz, data).thenRun(() -> {
            manager.writtenChunk(message.world, message.cx, message.cz);
            connection.sendReply(new BooleanMessageReply(true), message);
        });
    }

    private static void handleTransientEntities(ServerConnection connection, WriteChunkMessage message) {
        RegionFileCache.getChunkDeflatedDataAsync(message.path, message.cx, message.cz).thenAccept(data -> {
            CompoundTag transientEntities = CompoundTag.read(new DataInputStream(new ByteArrayInputStream(message.data))).asCompound();

            if (data != null) {
                CompoundTag existingEntities = CompoundTag.read(new DataInputStream(new ByteArrayInputStream(data))).asCompound();
                merge(existingEntities, transientEntities);
            }

            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                transientEntities.write(new DataOutputStream(buffer));
                writeData(connection, message, buffer.toByteArray());
            } catch (IOException e) {
                // Should be unreachable
                throw new RuntimeException(e);
            }
        });
    }

    private static void merge(CompoundTag from, CompoundTag to) {
        if (from == null) {
            return;
        }

        ListTag entitiesFrom = from.get("Entities").asList();
        if (entitiesFrom == null || entitiesFrom.isEmpty()) {
            return;
        }

        ListTag entitiesTo = to.get("Entities").asList();
        to.set("Entities", entitiesFrom);
        for (SpecificTag tag : entitiesTo) {
            // The 'from' entities must appear in the list before the 'to' entities
            entitiesFrom.add(tag);
        }
    }
}
