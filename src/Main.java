import org.webencode.Bencode;
import org.wetorrent.Metafile;
import org.wetorrent.Tracker;
import org.wetorrent.util.Utils;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class Main {

    private static String charset = "UTF-8";
    private static Peer self;
    private static Metafile metafile;
    private static ArrayList<Piece> pieces;

    private static final int blockSize = 16384;
    private static final int testBlockSize = 20;

    public static void main(String[] args) throws Exception {
        useBitletTracker(args);
//        test(args);
    }

    private static ByteBuffer wrap(String s) {
        return ByteBuffer.wrap(s.getBytes());
    }

    private static void test(String[] args) throws Exception {
        // get args
        String filename = args[0];
        ServerSocket serverSocket = new ServerSocket(0);
        int myPort = serverSocket.getLocalPort();
        serverSocket.close();
        // generate my peer id
        byte[] myPeerId = new byte[20];
        new Random().nextBytes(myPeerId);

        pieces = new ArrayList<Piece>();
        for (int i = 0; i < 5; i++) {
            pieces.add(i, new Piece(i, filename));
        }

        int pieceSize = 20;

        int uploaded = 0;
        int downloaded = 0;
        int complete = 0;

        // create peer for self
        self = new Peer(null, myPort);

        // try connecting with first peer
        Peer peer = new Peer(InetAddress.getLocalHost(), 55555);
        System.out.println("creating socket...");
        Socket socket = new Socket(peer.getIp(), peer.getPort(), null, myPort);
        System.out.println("successful");

        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        Message message = receiveMessage(in);

        // figure out which pieces peer has
        while (message.getType().equals(MessageType.BITFIELD) || message.getType().equals(MessageType.HAVE)) {
            try {
                if (message.getType().equals(MessageType.BITFIELD)) {
                    peer.setBitfield(pieces.size(), message.getPayload());
                } else if (message.getType().equals(MessageType.HAVE)) {
                    peer.addPiece(ByteBuffer.wrap(message.getPayload()).order(ByteOrder.BIG_ENDIAN).getInt());
                } else {
                    System.out.println("received message of type " + message.getType());
                    System.exit(1);
                }
            } catch (Exception e) {
                socket.close();
                e.printStackTrace();
                System.exit(1);
            }
            message = receiveMessage(in);
            if (message == null) {
                System.out.println("got null message");
                break;
            }
        }

        // send interested and unchoke message
        sendMessage(new Message(MessageType.INTERESTED), out);
        peer.setAmInterested(true);
        sendMessage(new Message(MessageType.UNCHOKE), out);
        peer.setIsChoked(false);

        // wait for unchoked message
        System.out.println("waiting for unchoke message...");
        while (!message.getType().equals(MessageType.UNCHOKE) && !socket.isClosed() && socket.isConnected()) {
            sendMessage(new Message(MessageType.INTERESTED), out);
            Thread.sleep(5000);
            message = receiveMessage(in);
        }
        if (socket.isClosed()) {
            System.out.println("socket closed");
            System.exit(1);
        }
        System.out.println("unchoked");
        peer.setAmChoked(false);

        int curr = 0;
        while(curr != -1) {
            ByteBuffer requestPayload = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
            requestPayload.putInt(0);    // put piece index
            requestPayload.putInt(0);                           // put block offset
            requestPayload.putInt(testBlockSize);                   // put block size

            Message request = new Message(MessageType.REQUEST, requestPayload.array());
            sendMessage(request, out);

            // wait for piece message
            message = receiveMessage(in);
            while (!message.getType().equals(MessageType.PIECE)) {
                message = receiveMessage(in);
            }
            // add payload to piece
            if (pieces.get(curr) == null) {
                pieces.set(curr, new Piece(curr, filename));
            }
            ByteBuffer b = ByteBuffer.wrap(message.getPayload());
            if (curr != b.getInt()) {
                System.out.println("incorrect piece index!");
                System.exit(1);
            }
            if (pieces.get(curr).getOffset() != b.getInt()) {
                System.out.println("incorrect offset!");
                System.exit(1);
            }
            pieces.get(curr).writeData(b.array());
            if (pieces.get(curr).getOffset() == pieceSize) {
                self.addPiece(curr);
            } else if (curr == pieces.size() - 1 && pieces.get(curr).getOffset() == 100 % pieceSize) {
                self.addPiece(curr);
            }
            curr = nextPieceToRequest(peer);
        }

        System.exit(0);
    }

    private static void useBitletTracker(String[] args) throws Exception {
        // get args
        String torrentFilename = args[0];
        String filename = torrentFilename.split(".torrent")[0];
        ServerSocket serverSocket = new ServerSocket(0);
        int myPort = serverSocket.getLocalPort();
        serverSocket.close();
        // generate my peer id
        byte[] myPeerId = new byte[20];
        new Random().nextBytes(myPeerId);

        // parse metafile
        metafile = new Metafile(new BufferedInputStream(new FileInputStream(torrentFilename)));

        pieces = new ArrayList<Piece>();
        for (int i = 0; i < metafile.getPieces().size(); i++) {
            pieces.add(i, new Piece(i, filename));
        }

        int uploaded = 0;
        int downloaded = 0;
        int complete = 0;

        // create tracker
        Tracker tracker = new Tracker(metafile.getAnnounce());

        // get the tracker response!
        Map trackerResponse = tracker.trackerRequest(
                metafile.getInfoSha1Encoded(),
                Utils.byteArrayToURLString(myPeerId),
                myPort,
                uploaded,
                downloaded,
                metafile.getLength(),
                "started"
        );

        String failureReason = (String) trackerResponse.get(wrap("failure reason"));
        if (failureReason != null) {
            System.out.println("Tracker response failed: " + failureReason);
        }

        // get list of peers from byte string
        List<Peer> peers = parsePeers(((ByteBuffer) trackerResponse.get(wrap("peers"))).array());

        // create peer for self
        self = new Peer(null, myPort);

        // try connecting with first peer
        Peer peer = peers.get(0);
        System.out.println("creating socket...");
        Socket socket = new Socket(peer.getIp(), peer.getPort(), null, myPort);
        System.out.println("successful");

        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        // send handshake
        out.writeByte(19);                                                                          // send protocol name length
        out.write("BitTorrent protocol".getBytes(), 0, "BitTorrent protocol".getBytes().length);    // send protocol name
        out.write(new byte[8], 0, 8);                                                               // send reserved bytes
        out.write(metafile.getInfoSha1(), 0, metafile.getInfoSha1().length);                        // send info hash
        out.write(myPeerId, 0, myPeerId.length);                                                    // send peer id

        // get peer handshake
        byte protocolLen = in.readByte();
        byte[] peerInfoHash = new byte[20];
        byte[] peerId = new byte[20];

        if (protocolLen != (byte) 19) {
            System.out.println("wrong protocol name length!");
            System.exit(1);
        } else {


            in.skipBytes(19 + 8); // skip protocol name and reserved bytes
            in.read(peerInfoHash, 0, 20);
            in.read(peerId, 0, 20);
        }

        // send bitfield, interested, unchoke
        sendMessage(new Message(MessageType.BITFIELD, new byte[metafile.getPieces().size()]), out);
        sendMessage(new Message(MessageType.INTERESTED), out);
        peer.setAmInterested(true);
        sendMessage(new Message(MessageType.UNCHOKE), out);
        peer.setIsChoked(false);

        Message message = receiveMessage(in);

        // figure out which pieces peer has
        while (message.getType().equals(MessageType.BITFIELD) || message.getType().equals(MessageType.HAVE)) {
            try {
                if (message.getType().equals(MessageType.BITFIELD)) {
                    peer.setBitfield(metafile.getPieces().size(), message.getPayload());
                } else if (message.getType().equals(MessageType.HAVE)) {
                    peer.addPiece(ByteBuffer.wrap(message.getPayload()).order(ByteOrder.BIG_ENDIAN).getInt());
                } else {
                    System.out.println("received message of type " + message.getType());
                    System.exit(1);
                }
            } catch (Exception e) {
                socket.close();
                e.printStackTrace();
                System.exit(1);
            }
            message = receiveMessage(in);
            if (message == null) {
                System.out.println("got null message");
                break;
            }
        }

        // wait for unchoked message
        System.out.println("waiting for unchoke message...");
        while (!message.getType().equals(MessageType.UNCHOKE) && !socket.isClosed() && socket.isConnected()) {
            sendMessage(new Message(MessageType.INTERESTED), out);
            Thread.sleep(5000);
            message = receiveMessage(in);
        }
        if (socket.isClosed()) {
            System.out.println("socket closed");
            System.exit(1);
        }
        System.out.println("unchoked");
        peer.setAmChoked(false);

        int curr = nextPieceToRequest(peer);
        while(curr != -1) {
            ByteBuffer requestPayload = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
            requestPayload.putInt(0);    // put piece index
            requestPayload.putInt(0);                           // put block offset
            requestPayload.putInt(blockSize);                   // put block size

            Message request = new Message(MessageType.REQUEST, requestPayload.array());
            sendMessage(request, out);

            // wait for piece message
            message = receiveMessage(in);
            while (!message.getType().equals(MessageType.PIECE)) {
                message = receiveMessage(in);
            }
            // add payload to piece
            if (pieces.get(curr) == null) {
                pieces.set(curr, new Piece(curr, filename));
            }
            ByteBuffer b = ByteBuffer.wrap(message.getPayload());
            if (curr != b.getInt()) {
                System.out.println("incorrect piece index!");
                System.exit(1);
            }
            if (pieces.get(curr).getOffset() != b.getInt()) {
                System.out.println("incorrect offset!");
                System.exit(1);
            }
            pieces.get(curr).writeData(b.array());
            if (pieces.get(curr).getOffset() == metafile.getPieceLength()) {
                self.addPiece(curr);
            } else if (curr == metafile.getPieces().size() - 1 && pieces.get(curr).getOffset() == metafile.getLength() % metafile.getPieceLength()) {
                self.addPiece(curr);
            }
            curr = nextPieceToRequest(peer);
        }

        System.exit(0);

    }

    private static List<Peer> parsePeers(byte[] peers) throws IOException {
        /*
        peers should have a length that is a multiple of 6.
        every 6 bytes represents a peer.
        the first 4 bytes are the ip and the last 2 are the port.
        */
        if (peers.length % 6 != 0) {
            System.out.println("corrupted peer string");
            return null;
        }
        List<Peer> peerList = new LinkedList<Peer>();
        DataInputStream peerStream = new DataInputStream(new ByteArrayInputStream(peers));
        while (peerStream.available() > 0) {
            byte[] ip = new byte[4];
            byte[] port = new byte[4];
            peerStream.read(ip, 0, 4);
            port[2] = peerStream.readByte();
            port[3] = peerStream.readByte();
            peerList.add(new Peer(InetAddress.getByAddress(ip), ByteBuffer.wrap(port).getInt()));
        }
        return peerList;
    }

    private static void sendMessage(Message message, DataOutputStream out) throws IOException {
        try {
            out.write(message.toByteArray(), 0, message.toByteArray().length);
            out.flush();
        } catch (SocketException e) {
//            e.printStackTrace();
            System.out.println("failed to send message");
        }
    }

    private static Message receiveMessage(DataInputStream in) throws IOException {
        byte[] len = new byte[4];
        in.read(len, 0, 4);
        if (ByteBuffer.wrap(len).order(ByteOrder.BIG_ENDIAN).getInt() == 0) {
            return new Message(MessageType.KEEP_ALIVE);
        }

        byte id = in.readByte();

        int payloadLength = ByteBuffer.wrap(len).order(ByteOrder.BIG_ENDIAN).getInt() - 1;
        byte[] payload;
        try {
            payload = new byte[payloadLength];
        } catch (NegativeArraySizeException e) {
            return null;
        }
        in.read(payload, 0, payloadLength);

        Message message = null;
        switch (id) {
            case 0: message = new Message(MessageType.CHOKE); break;
            case 1: message = new Message(MessageType.UNCHOKE); break;
            case 2: message = new Message(MessageType.INTERESTED); break;
            case 3: message = new Message(MessageType.NOT_INTERESTED); break;
            case 4: message = new Message(MessageType.HAVE, payload); break;
            case 5: message = new Message(MessageType.BITFIELD, payload); break;
            case 6: message = new Message(MessageType.REQUEST, payload); break;
            case 7: message = new Message(MessageType.PIECE, payload); break;
            case 8: message = new Message(MessageType.CANCEL, payload); break;
            case 9: message = new Message(MessageType.PORT, payload); break;
            default: System.out.println("invalid message id!");
        }
        System.out.println(message.getType());
        return message;
    }

    private static byte[] readLength(DataInputStream in) throws IOException {
        byte[] len = new byte[4];
        in.read(len, 0, 4);
        return len;
    }

    private static byte readId(DataInputStream in) throws IOException {
        return in.readByte();
    }

    private static int nextPieceToRequest(Peer peer) {
        for (int i = 0; i < metafile.getPieces().size(); i++) {
            if (!self.hasPiece(i) && peer.hasPiece(i)) {
                return i;
            }
        }
        return -1; // I don't need any of this peer's pieces
    }

}
