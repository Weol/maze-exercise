package mazeoblig;

import java.io.Serializable;

/**
 * A simple class to used to communicate that the amount of players in a specifc (x, y) point in the maze has changed
 */
public class PositionChange implements Serializable {

    public int x, y;
    public int diff;

    public PositionChange(int x, int y, int diff) {
        this.x = x;
        this.y = y;
        this.diff = diff;
    }

}
