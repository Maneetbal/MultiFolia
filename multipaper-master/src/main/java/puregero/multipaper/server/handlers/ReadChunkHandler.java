package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.MasterBoundMessage;
import puregero.multipaper.mastermessagingprotocol.messages.masterbound.ReadChunkMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.ChunkLoadedOnAnotherServerMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.DataMessageReply;
import puregero.multipaper.server.ChunkSubscriptionManager;
import puregero.multipaper.server.EntitiesSubscriptionManager;
import puregero.multipaper.server.LockManager;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.util.RegionFileCache;

public class ReadChunkHandler {
    public static void handle(ServerConnection connection, ReadChunkMessage message) {
        if (checkIfLoadedOnAnotherServer(connection, message.world, message.type, message.cx, message.cz, message)) {
            return;
        }

        ReadChunkHandler.readChunk(connection, message.world, message.path, message.type, message.cx, message.cz, message);
    }

    public static void readChunk(ServerConnection connection, String world, String path, String type, int cx, int cz, MasterBoundMessage message) {
        LockManager.getLockManager(type).waitForLock(world, cx, cz, () -> RegionFileCache.getChunkDeflatedDataAsync(path, cx, cz).thenAccept(b -> {
            if (b == null) {
                b = new byte[0];
            }
            connection.sendReply(new DataMessageReply(b), message);
        }));
    }

    private static boolean checkIfLoadedOnAnotherServer(ServerConnection connection, String world, String type, int cx, int cz, ReadChunkMessage message) {
        switch (type) {
            case "poi" -> {
                return false;
            }
            case "region" -> {
                ServerConnection alreadyLoadedChunk = ChunkSubscriptionManager.getOwnerOrSubscriber(world, cx, cz);
                ChunkSubscriptionManager.subscribe(connection, world, cx, cz);

                if (alreadyLoadedChunk == null || alreadyLoadedChunk == connection) {
                    return false;
                }

                connection.sendReply(new ChunkLoadedOnAnotherServerMessage(alreadyLoadedChunk.getBungeeCordName()), message);
                return true;
            }
            case "entities" -> {
                ServerConnection alreadyLoadedEntities = EntitiesSubscriptionManager.getSubscriber(world, cx, cz);
                EntitiesSubscriptionManager.subscribe(connection, world, cx, cz);

                if (alreadyLoadedEntities == null || alreadyLoadedEntities == connection) {
                    return false;
                }

                connection.sendReply(new ChunkLoadedOnAnotherServerMessage(alreadyLoadedEntities.getBungeeCordName()), message);
                return true;
            }
        }

        throw new IllegalArgumentException("Unknown data type " + type);
    }
}
