package mazeoblig;

import java.io.Serializable;

public class PositionChange implements Serializable {

    public int x, y;
    public int diff;

    public PositionChange(int x, int y, int diff) {
        this.x = x;
        this.y = y;
        this.diff = diff;
    }

}
