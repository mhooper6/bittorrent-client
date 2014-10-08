import java.net.InetAddress;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Represents a BitTorrent peer
 */
public class Peer {
    private InetAddress ip;
    private int port;

    private boolean isChoked;
    private boolean isInterested;
    private boolean amChoked;
    private boolean amInterested;

    private HashSet<Integer> pieces;

    public Peer(InetAddress ip, int port) {
        this.ip = ip;
        this.port = port;

        isChoked = true;
        isInterested = false;
        amChoked = true;
        amInterested = false;

        pieces = new HashSet<Integer>();
    }

    public InetAddress getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public boolean isChoked() {
        return isChoked;
    }

    public void setIsChoked(boolean choked) {
        isChoked = choked;
    }

    public boolean isInterested() {
        return isInterested;
    }

    public void setIsInterested(boolean interested) {
        isInterested = interested;
    }

    public boolean amChoked() {
        return amChoked;
    }

    public void setAmChoked(boolean amChoked) {
        this.amChoked = amChoked;
    }

    public boolean amInterested() {
        return amInterested;
    }

    public void setAmInterested(boolean amInterested) {
        this.amInterested = amInterested;
    }

    public void setBitfield(int n, byte[] bitfield) {
        for (int i = 0; i < n; i++) {
            byte b = bitfield[i / 8];
            byte mask = (byte) (1 << (7 - (i % 8)));
            if ((b & mask) != 0) {
                pieces.add(i);
            }

        }
    }

    public void addPiece(int p) {
        if (!pieces.contains(p)) {
            pieces.add(p);
        }
    }

    public boolean hasPiece(int p) {
        return pieces.contains(p);
    }

}
