package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.ReadFileMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.DataMessageReply;
import puregero.multipaper.server.FileLocker;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.util.Async;

import java.io.File;
import java.io.IOException;

public class ReadFileHandler {
    public static void handle(ServerConnection connection, ReadFileMessage message) {
        Async.run(() -> {
            try {
                byte[] b = FileLocker.readBytes(new File(message.path));
                connection.sendReply(new DataMessageReply(b), message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
