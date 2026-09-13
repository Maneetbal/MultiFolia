package puregero.multipaper.server.handlers;

import puregero.multipaper.mastermessagingprotocol.messages.masterbound.PingMessage;
import puregero.multipaper.mastermessagingprotocol.messages.serverbound.BooleanMessageReply;
import puregero.multipaper.server.ServerConnection;
import puregero.multipaper.server.util.Async;

public class PingHandler {
    public static void handle(ServerConnection connection, PingMessage message) {
        Async.run(() -> {
            connection.sendReply(new BooleanMessageReply(true), message);
        });
    }
}
