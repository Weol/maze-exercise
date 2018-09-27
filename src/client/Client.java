package client;

import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import mazeoblig.Box;
import mazeoblig.BoxMazeInterface;
import mazeoblig.Maze;

import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.rmi.Naming;

public class Client {

    public static void main(String[] args) {
        Maze maze = new Maze();

        JFrame frame = new JFrame();
        frame.setLayout(new GridLayout(1, 1));
        frame.add(maze);
        frame.setSize(Maze.DIM * 10 + 10, Maze.DIM * 10 + 40);
        frame.setResizable(false);

        maze.init();
        maze.start();

        frame.setVisible(true);
    }

}
