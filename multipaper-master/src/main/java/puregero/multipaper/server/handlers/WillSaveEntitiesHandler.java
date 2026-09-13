package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.WillSaveEntitiesLaterMessage;
import puregero.multipaper.server.LockManager;
import puregero.multipaper.server.ServerConnection;

public class WillSaveEntitiesHandler {
    public static void handle(ServerConnection connection, WillSaveEntitiesLaterMessage message) {
        LockManager.ENTITIES_MANAGER.lockUntilWrite(message.world, message.cx, message.cz);
    }
}
