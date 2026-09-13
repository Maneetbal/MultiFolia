package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.WriteDataMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.BooleanMessageReply;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.DataUpdateMessage;
import puregero.multipaper.server.FileLocker;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.util.Async;

import java.io.File;
import java.io.IOException;

public class WriteDataHandler {
    public static void handle(ServerConnection connection, WriteDataMessage message) {
        Async.run(() -> {
            try {
                FileLocker.writeBytes(new File(message.path), message.data);
                connection.sendReply(new BooleanMessageReply(true), message);

                if (message.identifier.equals("minecraft:scoreboard") || message.identifier.equals("minecraft:world_border")) {
                    // scoreboard and world_border are synced with other methods
                    return;
                }

                connection.broadcastOthers(new DataUpdateMessage(message.path, message.identifier, message.data));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
