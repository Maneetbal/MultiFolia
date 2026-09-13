package puregero.multipaper.mastermessagingprotocol.messages.serverbound;

import puregero.multipaper.mastermessagingprotocol.ExtendedByteBuf;

public class DataUpdateMessage extends ServerBoundMessage {

    public final String path;
    public final String identifier;
    public final byte[] data;

    public DataUpdateMessage(String path, String identifier, byte[] data) {
        this.path = path;
        this.identifier = identifier;
        this.data = data;
    }

    public DataUpdateMessage(ExtendedByteBuf byteBuf) {
        path = byteBuf.readString();
        identifier = byteBuf.readString();
        data = new byte[byteBuf.readVarInt()];
        byteBuf.readBytes(data);
    }

    @Override
    public void write(ExtendedByteBuf byteBuf) {
        byteBuf.writeString(path);
        byteBuf.writeString(identifier);
        byteBuf.writeVarInt(data.length);
        byteBuf.writeBytes(data);
    }

    @Override
    public void handle(ServerBoundMessageHandler handler) {
        handler.handle(this);
    }
}
