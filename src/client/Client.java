package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import mazeoblig.*;
import mazeoblig.Box;
import simulator.PositionInMaze;
import simulator.VirtualUser;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Timer;

public class Client extends Application {

    private Stage stage;
    private Canvas playerCanvas;

    private Map<IPlayer, PositionInMaze> players;

    Box[][] boxMaze;

    public static void main(String[] args) throws RemoteException, NotBoundException {
        launch();
    }

    @Override
    public void start(Stage stage) throws Exception {
        players = new Hashtable<>();
        this.stage = stage;

        Registry registry = LocateRegistry.getRegistry(RMIServer.getHostName(), RMIServer.getRMIPort());
        IUserRegistry userRegistry = (IUserRegistry) registry.lookup(RMIServer.UserRegistryName);

        userRegistry.register(new User());
    }

    public void paintMaze(GraphicsContext g, Box[][] boxMaze) {
        g.setStroke(Color.DARKGRAY);
        g.setLineWidth(2);

        double w = g.getCanvas().widthProperty().get();
        double h = g.getCanvas().heightProperty().get();

        g.clearRect(0,0, w, h);

        int dimension = boxMaze.length;

        for (int x = 1; x < (boxMaze.length - 1); ++x) {
            for (int y = 1; y < (boxMaze.length - 1); ++y) {
                if (boxMaze[x][y].getUp() == null)
                    g.strokeLine(x * w / dimension, y * h / dimension, x * w / dimension + w / dimension, y * h / dimension);
                if (boxMaze[x][y].getDown() == null)
                    g.strokeLine(x * w / dimension, y * h / dimension + h / dimension, x * w / dimension + w / dimension, y * h / dimension + h / dimension);
                if (boxMaze[x][y].getLeft() == null)
                    g.strokeLine(x * w / dimension, y * h / dimension, x * w / dimension, y * h / dimension + h / dimension);
                if (boxMaze[x][y].getRight() == null)
                    g.strokeLine(x * w / dimension + w / dimension, y * h / dimension, x * w / dimension + w / dimension, y * h / dimension + h / dimension);
            }
        }
    }

    public void paintPositions(GraphicsContext g, Map<IPlayer, PositionInMaze> map, Box[][] boxMaze) {
        g.setFill(Color.BLUE);
        g.setLineWidth(2);

        double w = g.getCanvas().widthProperty().get();
        double h = g.getCanvas().heightProperty().get();

        g.clearRect(0,0, w, h);

        int dimension = boxMaze.length;
        int radius = 4;

        System.out.println(System.currentTimeMillis());

        for (PositionInMaze position : map.values()) {
            int x = position.getXpos() + 1;
            int y = position.getYpos() + 1;

            g.fillOval(x * w / dimension - w / dimension / 2 - radius / 2, y * h / dimension - h / dimension / 2 - radius / 2, radius, radius);
        }
    }

    public class User extends UnicastRemoteObject implements IUser {

        protected User() throws RemoteException {
        }

        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            boxMaze = gameServer.getMaze().getMaze();
            for (IPlayer iPlayer : gameServer.getPlayers()) {
                players.put(iPlayer, iPlayer.getPosition());
            }

            Platform.runLater(() -> {
                Pane pane = new Pane();

                Canvas mazeCanvas = new Canvas();
                mazeCanvas.layoutXProperty().bind(pane.widthProperty().divide(2).subtract(mazeCanvas.widthProperty().divide(2)));
                mazeCanvas.layoutYProperty().bind(pane.heightProperty().divide(2).subtract(mazeCanvas.heightProperty().divide(2)));

                pane.widthProperty().addListener((observable, old, now) -> {
                    double min = Math.min(pane.widthProperty().get(), pane.heightProperty().get());
                    mazeCanvas.setHeight(min);
                    mazeCanvas.setWidth(min);

                    paintMaze(mazeCanvas.getGraphicsContext2D(), boxMaze);
                });

                pane.heightProperty().addListener((observable, old, now) -> {
                    double min = Math.min(pane.widthProperty().get(), pane.heightProperty().get());
                    mazeCanvas.setHeight(min);
                    mazeCanvas.setWidth(min);

                    paintMaze(mazeCanvas.getGraphicsContext2D(), boxMaze);
                });

                playerCanvas = new Canvas();
                playerCanvas.widthProperty().bind(mazeCanvas.widthProperty());
                playerCanvas.heightProperty().bind(mazeCanvas.heightProperty());
                playerCanvas.layoutXProperty().bind(mazeCanvas.layoutXProperty());
                playerCanvas.layoutYProperty().bind(mazeCanvas.layoutYProperty());

                pane.getChildren().add(mazeCanvas);
                pane.getChildren().add(playerCanvas);

                stage.setScene(new Scene(pane));
                stage.minWidthProperty().set(boxMaze.length * 10);
                stage.minHeightProperty().set(boxMaze.length * 10 + 30);

                stage.show();
            });

            VirtualUser virtualUser = new VirtualUser(boxMaze);
            new Thread(() -> {
                while (true) {
                    for (PositionInMaze positionInMaze : virtualUser.getIterationLoop()) {
                        try {
                            player.moveTo(positionInMaze);
                            Thread.sleep(300);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }

        @Override
        public void onPlayerConnected(IPlayer player) throws RemoteException {
            players.put(player, player.getPosition());
        }

        @Override
        public void onPlayerDisconnected(IPlayer player) throws RemoteException {
            players.remove(player);
        }

        @Override
        public boolean onLeaseExpired() throws RemoteException {
            return true;
        }

        @Override
        public void onPlayerPositionsChange(Map<IPlayer, PositionInMaze> positions) throws RemoteException {
            System.out.format("Updating %d positions\n", positions.size());
            players.putAll(positions);
            paintPositions(playerCanvas.getGraphicsContext2D(), players, boxMaze);
        }

    }

}
