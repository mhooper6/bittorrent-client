/**
 * Represents an enumerated BitTorrent message type
 */
public enum MessageType {
    KEEP_ALIVE((byte) -1),
    CHOKE((byte) 0),
    UNCHOKE((byte) 1),
    INTERESTED((byte) 2),
    NOT_INTERESTED((byte) 3),
    HAVE((byte) 4),
    BITFIELD((byte) 5),
    REQUEST((byte) 6),
    PIECE((byte) 7),
    CANCEL((byte) 8),
    PORT((byte) 9);

    private final byte id;

    MessageType(byte id) {
        this.id = id;
    }

    public byte getId() {
        return id;
    }
}
