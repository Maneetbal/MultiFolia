package puregero.multipaper.externalserverprotocol;

import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import puregero.multipaper.ExternalServerConnection;

import java.net.SocketException;

public class ExternalServerPacketHandler extends SimpleChannelInboundHandler<ExternalServerPacket> {
    private static final Logger LOGGER = LogUtils.getClassLogger();
    private final ExternalServerConnection connection;
    private boolean disconnectedWithException = false;

    public ExternalServerPacketHandler(ExternalServerConnection connection) {
        this.connection = connection;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ExternalServerPacket msg) {
        connection.lastPacketReceived = System.currentTimeMillis();
        msg.handle(connection);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) {
        if (ctx.channel().isOpen()) {
            if (throwable instanceof SocketException) {
                disconnectedWithException = true;
                if (connection.externalServer != null) {
                    LOGGER.info("External server {} has disconnected: {}", connection.externalServer.getName(), throwable.getMessage());
                }
            } else {
                throw new RuntimeException(throwable);
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        connection.nanoTime = 0;
        if (!disconnectedWithException && connection.externalServer != null) {
            LOGGER.info("External server {} has disconnected", connection.externalServer.getName());
        }
    }
}
