package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.WriteFileMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.BooleanMessageReply;
import puregero.multipaper.server.FileLocker;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.util.Async;

import java.io.File;
import java.io.IOException;

public class WriteFileHandler {
    public static void handle(ServerConnection connection, WriteFileMessage message) {
        Async.run(() -> {
            try {
                FileLocker.writeBytes(new File(message.path), message.data);
                connection.sendReply(new BooleanMessageReply(true), message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
