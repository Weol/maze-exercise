package mazeoblig;

import java.io.Serializable;

/**
 * A simple class that contains a 2-dimensional array that represents a maze and how many players are positioned in
 * every position within it, and a index that denotes which tick this map was used. The index is used top synchronize
 * MapChangeEvents from the server.
 */
public class PlayerMap implements Serializable {

    private int[][] map;
    private long index;

    public PlayerMap(int[][] map, long index) {
        this.map = map;
        this.index = index;
    }

    public int[][] getMap() {
        return map;
    }

    public long getIndex() {
        return index;
    }

}
