import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Represents a piece of a torrent file
 */
public class Piece {
    private int index;
    private int offset;
    private FileOutputStream out;

    public Piece(int index, String filename) {
        this.index = index;
        this.offset = 0;
        try {
            this.out = new FileOutputStream(filename + index);
        } catch (FileNotFoundException e) {
            System.out.print("Failed to create file output stream: ");
            e.printStackTrace();
        }
    }

    public void writeData(byte[] buffer) throws IOException {
        out.write(buffer, 8, buffer.length - 8);
        offset += buffer.length - 8;
    }

    public int getIndex() {
        return index;
    }

    public int getOffset() {
        return offset;
    }

}
