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
 * This class is used for notifying users about changes, it contains an array of changes and a index that identifies the
 * change.
 */
public class MapChangeEvent implements Serializable {

    private int[][] changes; //The changes
    private long index; //The identifier for the change

    private int size; //The amount of changes contained in this instance

    /**
     * Constructs this class and sets the size of {@link #changes}, this is the maximum amount of changes that this
     * MapChangeEvent can contain.
     *
     * @param maximumAmountOfChanges the maximum amount of changes
     */
    public MapChangeEvent(int maximumAmountOfChanges) {
        changes = new int[maximumAmountOfChanges][3];
        size = 0;
    }

    /**
     * @return the amount of changes
     */
    public int size() {
        return size;
    }

    /**
     * Adds a new change to this MapChangeEvent
     *
     * @param x the x-position of the change
     * @param y the y-position of the change
     * @param difference the change
     */
    public synchronized void add(int x, int y, int difference) {
        changes[size] = new int[]{x, y ,difference};
        size++;
    }

    /**
     * Gets a change from this MapChangeEvent, the indexes of the three dimensional array is mapped like this:
     *  changes[i][0] = the x-value of the position of the change
     *  changes[i][1] = the y-value of the position of the change
     *  changes[i][2] = the change in amount of players
     *
     * @param i the index of the change
     * @return
     */
    public int[] get(int i) {
        return changes[i];
    }

    /**
     * Gets the identifier for this MapChangeEvent, used to synchronize out of order MapChangeEvents
     *
     * @return the identifier for the MapChangeEvent
     */
    public long getIndex() {
        return index;
    }

    /**
     * Sets the identifer for this MapChangeEvent
     *
     * @param index the identifer
     */
    public void setIndex(long index) {
        this.index = index;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeLong(index);
        out.writeInt(size);
        out.writeObject(Arrays.copyOfRange(changes, 0, size));
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        index = in.readLong();
        size = in.readInt();
        changes = (int[][]) in.readObject();
    }
}
