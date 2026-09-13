package puregero.multipaper.server.proxy;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class HelloPacket {

    private final int protocolVersion;
    public String hostName;
    private final short port;
    private final int intention;

    public HelloPacket(int protocolVersion, String hostName, short port, int intention) {
        this.protocolVersion = protocolVersion;
        this.hostName = hostName;
        this.port = port;
        this.intention = intention;
    }

    public static HelloPacket read(ByteBuffer buffer) {
        int length = buffer.get() & 0xFF;
        if (length == 0xFE) {
            throw new IllegalArgumentException("Legacy ping packet");
        }

        if (length > 127) {
            throw new IllegalArgumentException("Handshake packet length > 127");
        }

        int packetId = readVarInt(buffer);
        if (packetId != 0) {
            throw new IllegalArgumentException("Unknown handshake packet id " + packetId);
        }

        int protocolVersion = readVarInt(buffer);
        String hostName = readString(buffer);
        short port = buffer.getShort();
        int intention = readVarInt(buffer);
        return new HelloPacket(protocolVersion, hostName, port, intention);
    }

    public void write(ByteBuffer buffer) {
        int lengthPosition = buffer.position();
        buffer.position(lengthPosition + 1);

        writeVarInt(buffer, 0);
        writeVarInt(buffer, protocolVersion);
        writeVarInt(buffer, hostName.getBytes(StandardCharsets.UTF_8).length);
        buffer.put(hostName.getBytes(StandardCharsets.UTF_8));
        buffer.putShort(port);
        writeVarInt(buffer, intention);

        int packetLength = buffer.position() - lengthPosition - 1;
        if (packetLength > 127) {
            throw new IllegalArgumentException("HelloPacket written length is " + packetLength + "!!!");
        }

        buffer.put(lengthPosition, (byte) packetLength);
    }

    private static int readVarInt(ByteBuffer buffer) {
        int value = 0;
        int length = 0;
        byte currentByte;

        do {
            currentByte = buffer.get();
            value |= (currentByte & 0x7F) << (length * 7);

            length += 1;
            if (length > 5) {
                throw new RuntimeException("VarInt is too big");
            }
        } while ((currentByte & 0x80) == 0x80);

        return value;
    }

    private static void writeVarInt(ByteBuffer buffer, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                buffer.put((byte) value);
                return;
            }

            buffer.put((byte) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
    }

    private static String readString(ByteBuffer buffer) {
        int length = readVarInt(buffer);
        if (length < 0 || length > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid string length " + length);
        }

        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String sanitizeAddress(InetSocketAddress addr) {
        String string = addr.getHostString();

        // Remove IPv6 scope if present
        if (addr.getAddress() instanceof Inet6Address) {
            int strip = string.indexOf('%');
            return (strip == -1) ? string : string.substring(0, strip);
        } else {
            return string;
        }
    }
}
