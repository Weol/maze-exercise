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
import java.util.concurrent.ConcurrentHashMap;

public class Client extends Application {

    private Stage stage;
    private MazePane mazePane;

    private int[][] players;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;

        Registry registry = LocateRegistry.getRegistry(RMIServer.getHostName(), RMIServer.getRMIPort());
        IGameServer userRegistry = (IGameServer) registry.lookup(RMIServer.GameServerName);

        userRegistry.register(new UserImpl());
    }

    private class UserImpl extends User {

        protected UserImpl() throws RemoteException {
        }

        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            super.onGameReady(gameServer, player);

            players = gameServer.getPlayerMap();

            Platform.runLater(() -> {
                try {
                    mazePane = new MazePane(getMaze(), players);

                    mazePane.prefHeightProperty().bind(stage.heightProperty());
                    mazePane.prefWidthProperty().bind(stage.widthProperty());
                    mazePane.setVisible(true);

                    stage.setScene(new Scene(mazePane));
                    stage.minWidthProperty().set(getMaze().length * 10);
                    stage.minHeightProperty().set(getMaze().length * 10 + 30);

                    stage.show();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });

            VirtualUser virtualUser = new VirtualUser(getMaze());
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
                        mazePane.repaintPositions();
                    }
                }
            }).start();
        }

        @Override
        public void onPositionStateChange(PositionInMaze position, boolean occupied) throws RemoteException {
            if (players != null) {
                if (occupied) {
                    players[position.getXpos()][position.getYpos()] = 1;
                } else {
                    players[position.getXpos()][position.getYpos()] = 0;
                }
            }
        }

    }

}
