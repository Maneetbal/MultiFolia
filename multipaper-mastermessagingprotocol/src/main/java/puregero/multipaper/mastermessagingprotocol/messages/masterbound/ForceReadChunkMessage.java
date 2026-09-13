package puregero.multipaper.mastermessagingprotocol.messages.masterbound;

import puregero.multipaper.mastermessagingprotocol.ExtendedByteBuf;

public class ForceReadChunkMessage extends MasterBoundMessage {

    public final String world;
    public final String path;
    public final String type;
    public final int cx;
    public final int cz;

    public ForceReadChunkMessage(String world, String path, String type, int cx, int cz) {
        this.world = world;
        this.path = path;
        this.type = type;
        this.cx = cx;
        this.cz = cz;
    }

    public ForceReadChunkMessage(ExtendedByteBuf byteBuf) {
        world = byteBuf.readString();
        path = byteBuf.readString();
        type = byteBuf.readString();
        cx = byteBuf.readInt();
        cz = byteBuf.readInt();
    }

    @Override
    public void write(ExtendedByteBuf byteBuf) {
        byteBuf.writeString(world);
        byteBuf.writeString(path);
        byteBuf.writeString(type);
        byteBuf.writeInt(cx);
        byteBuf.writeInt(cz);
    }

    @Override
    public void handle(MasterBoundMessageHandler handler) {
        handler.handle(this);
    }
}
