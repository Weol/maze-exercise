package mazeoblig;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;

/**
 * A simple class to used to communicate that the amount of players in a specifc (xValues, y) point in the maze has changed
 */
public class PositionChange implements Serializable {

    private int[][] changes;
    private int hashcode;

    private int size;

    public PositionChange(int maximumAmountOfChanges, int hashcode) {
        changes = new int[maximumAmountOfChanges][3];
        this.hashcode = hashcode;
        size = 0;
    }

    public synchronized void add(int x, int y, int difference) {
        changes[size] = new int[]{x, y ,difference};
        size++;
    }

    public int getHashCode() {
        return hashcode;
    }

    public int[] get(int i) {
        return changes[i];
    }

    public int size() {
        return size;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeInt(hashcode);
        out.writeInt(size);
        out.writeObject(Arrays.copyOfRange(changes, 0, size));
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        hashcode = in.readInt();
        size = in.readInt();
        changes = (int[][]) in.readObject();
    }

}
