import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;

public class TestPeer {
    public static void main(String[] args) throws IOException {
        byte[] myPeerId = new byte[20];
        new Random().nextBytes(myPeerId);

        ServerSocket serverSocket = new ServerSocket(55555);
        Socket socket = serverSocket.accept();

        DataInputStream in = new DataInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        boolean interested = false;

        sendMessage(new Message(MessageType.KEEP_ALIVE), out);

        while(!interested) {
            Message message = receiveMessage(in);
            interested = message.getType().equals(MessageType.INTERESTED);
            sendMessage(new Message(MessageType.KEEP_ALIVE), out);
        }

        sendMessage(new Message(MessageType.UNCHOKE), out);

        ArrayList<byte[]> pieces = new ArrayList<byte[]>();
        pieces.add("line1line1line1line1".getBytes());
        pieces.add("line2line2line2line2".getBytes());
        pieces.add("line3line3line3line3".getBytes());
        pieces.add("line4line4line4line4".getBytes());
        pieces.add("line5line5line5line5".getBytes());

        while (true) {
            // wait for request message
            Message message = receiveMessage(in);
            while(!message.getType().equals(MessageType.REQUEST)) {
                message = receiveMessage(in);
            }

            ByteBuffer requestPayload = ByteBuffer.wrap(message.getPayload());
            int ind = requestPayload.getInt();
            int off = requestPayload.getInt();
            int blockSize = requestPayload.getInt();

            // create piece message payload
            ByteBuffer piecePayload = ByteBuffer.allocate(8 + blockSize);
            piecePayload.putInt(ind);
            piecePayload.putInt(off);
            byte[] piece = pieces.get(ind);
            for (int i = off; i < off + blockSize && i < piece.length; i++) {
                piecePayload.put(piece[i]);
            }

            // send message
            sendMessage(new Message(MessageType.PIECE, piecePayload.array()), out);
        }

    }

    private static void sendMessage(Message message, DataOutputStream out) throws IOException {
        try {
            out.write(message.toByteArray(), 0, message.toByteArray().length);
//            out.flush();
        } catch (SocketException e) {
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
        System.out.println(id);

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
        }
        return message;
    }

}
