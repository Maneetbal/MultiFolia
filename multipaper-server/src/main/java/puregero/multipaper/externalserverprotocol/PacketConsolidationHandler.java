package puregero.multipaper.externalserverprotocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import puregero.multipaper.config.MultiPaperConfiguration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class PacketConsolidationHandler extends ChannelDuplexHandler {
    private final int MAX_BUFFER_SIZE = 2 * 1024 * 1024;
    private final Executor consolidationDelay = CompletableFuture.delayedExecutor(MultiPaperConfiguration.get().peerConnection.consolidationDelay, TimeUnit.MILLISECONDS);
    private CompletableFuture<Void> consolidationFuture;
    private ChannelPromise bufferPromise;
    private ByteBuf buffer;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (this.buffer != null && (this.buffer.readableBytes() > MAX_BUFFER_SIZE || this.buffer.readableBytes() + ((ByteBuf) msg).readableBytes() > MAX_BUFFER_SIZE)) {
            ctx.writeAndFlush(this.buffer, this.bufferPromise);
            this.buffer = null;
        }

        if (((ByteBuf) msg).readableBytes() > MAX_BUFFER_SIZE) {
            ctx.writeAndFlush(msg, promise);
            return;
        }

        if (this.buffer == null) {
            this.buffer = ctx.alloc().buffer();
            this.bufferPromise = new DefaultChannelPromise(ctx.channel());
        }

        this.buffer.writeBytes((ByteBuf) msg);
        this.bufferPromise.addListener(p -> {
            if (p.isSuccess()) {
                promise.setSuccess();
            } else if (p.cause() != null) {
                promise.setFailure(p.cause());
            }
        });
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (this.consolidationFuture == null || this.consolidationFuture.isDone()) {
            this.consolidationFuture = new CompletableFuture<>();
            this.consolidationDelay.execute(() -> ctx.channel().eventLoop().execute(() -> {
                if (this.buffer != null) {
                    ctx.write(this.buffer, this.bufferPromise);
                    ctx.flush();
                }
                this.buffer = null;
                this.bufferPromise = null;
                this.consolidationFuture.complete(null);
            }));
        }
    }
}
