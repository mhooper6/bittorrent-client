import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Represents a BitTorrent message.
 */
public class Message {
    private int len;
    private MessageType type;
    private byte[] payload;

    public Message(MessageType type, byte[] payload) {
        this.type = type;
        this.payload = payload;
        this.len = 1 + this.payload.length;
    }

    public Message(MessageType type) {
        this.type = type;
        this.len = this.type.equals(MessageType.KEEP_ALIVE) ? 0 : 1;
    }

    public byte[] toByteArray() {
        ByteBuffer arr = ByteBuffer.allocate(len + 4).order(ByteOrder.BIG_ENDIAN);
        arr.putInt(len);
        if (!type.equals(MessageType.KEEP_ALIVE)) {
            arr.put(type.getId());
        }
        if (payload != null) {
            arr.put(payload);
        }
        return arr.array();
    }

    public int getLength() {
        return len;
    }

    public MessageType getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }
}
