package client;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import mazeoblig.Box;
import simulator.PositionInMaze;

import java.util.Map;

public class MazePane extends Pane {

    private Box[][] maze;

    private Canvas mazeCanvas;
    private Canvas playerCanvas;

    private int[][] positions;

    public MazePane(Box[][] maze, int[][] positions) {
        this.maze = maze;
        this.positions = positions;

        mazeCanvas = new Canvas();
        mazeCanvas.setVisible(true);
        mazeCanvas.layoutXProperty().bind(widthProperty().divide(2).subtract(mazeCanvas.widthProperty().divide(2)));
        mazeCanvas.layoutYProperty().bind(heightProperty().divide(2).subtract(mazeCanvas.heightProperty().divide(2)));

        playerCanvas = new Canvas();
        playerCanvas.setVisible(true);
        playerCanvas.widthProperty().bind(mazeCanvas.widthProperty());
        playerCanvas.heightProperty().bind(mazeCanvas.heightProperty());
        playerCanvas.layoutXProperty().bind(mazeCanvas.layoutXProperty());
        playerCanvas.layoutYProperty().bind(mazeCanvas.layoutYProperty());

        widthProperty().addListener((observable, old, now) -> {
            double min = Math.min(widthProperty().get(), heightProperty().get());
            mazeCanvas.setHeight(min);
            mazeCanvas.setWidth(min);

            paintMaze();
            paintPositions();
        });

        heightProperty().addListener((observable, old, now) -> {
            double min = Math.min(widthProperty().get(), heightProperty().get());
            mazeCanvas.setHeight(min);
            mazeCanvas.setWidth(min);

            paintMaze();
            paintPositions();
        });

        getChildren().add(mazeCanvas);
        getChildren().add(playerCanvas);
    }

    public void repaintMaze() {
        paintMaze();
    }

    public void repaintPositions() {
        paintPositions();
    }

    private void paintMaze() {
        GraphicsContext g = mazeCanvas.getGraphicsContext2D();

        g.setStroke(Color.RED);
        g.fillRect(0,0,200,200);

        g.setStroke(Color.DARKGRAY);
        g.setLineWidth(2);

        double w = g.getCanvas().widthProperty().get();
        double h = g.getCanvas().heightProperty().get();

        g.clearRect(0,0, w, h);

        int dimension = maze.length;

        for (int x = 1; x < (maze.length - 1); ++x) {
            for (int y = 1; y < (maze.length - 1); ++y) {
                if (maze[x][y].getUp() == null)
                    g.strokeLine(x * w / dimension, y * h / dimension, x * w / dimension + w / dimension, y * h / dimension);
                if (maze[x][y].getDown() == null)
                    g.strokeLine(x * w / dimension, y * h / dimension + h / dimension, x * w / dimension + w / dimension, y * h / dimension + h / dimension);
                if (maze[x][y].getLeft() == null)
                    g.strokeLine(x * w / dimension, y * h / dimension, x * w / dimension, y * h / dimension + h / dimension);
                if (maze[x][y].getRight() == null)
                    g.strokeLine(x * w / dimension + w / dimension, y * h / dimension, x * w / dimension + w / dimension, y * h / dimension + h / dimension);
            }
        }
    }

    private void paintPositions() {
        GraphicsContext g = playerCanvas.getGraphicsContext2D();

        g.setFill(Color.BLUE);
        g.setLineWidth(2);

        double w = g.getCanvas().widthProperty().get();
        double h = g.getCanvas().heightProperty().get();

        g.clearRect(0,0, w, h);

        int dimension = maze.length;
        int radius = 4;

        for (int x = 0; x < positions.length; x++) {
            for (int y = 0; y < positions[x].length; y++) {
                if (positions[x][y] > 0) {
                    g.fillOval((x + 1) * w / dimension - w / dimension / 2 - radius / 2, (y + 1) * h / dimension - h / dimension / 2 - radius / 2, radius, radius);
                }
            }
        }
    }

}
