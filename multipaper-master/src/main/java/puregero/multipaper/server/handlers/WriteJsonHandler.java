package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.WriteJsonMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.BooleanMessageReply;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.util.Async;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

public class WriteJsonHandler {
    public static final HashMap<String, byte[]> writesInProgress = new HashMap<>();
    private static final Object writingLock = new Object();

    public static void handle(ServerConnection connection, WriteJsonMessage message) {
        Async.run(() -> {
            try {
                synchronized (writingLock) {
                    synchronized (writesInProgress) {
                        writesInProgress.put(message.file, message.data);
                    }

                    Files.write(new File(message.file).toPath(), message.data);

                    synchronized (writesInProgress) {
                        writesInProgress.remove(message.file);
                    }
                }

                connection.sendReply(new BooleanMessageReply(true), message);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
