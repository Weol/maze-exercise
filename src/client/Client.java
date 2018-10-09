package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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

        private PositionInMaze position;

        protected UserImpl() throws RemoteException {
        }

        private void movePlayer(int dx, int dy) {
            try {
                boolean moveSuccessful = getPlayer().moveTo(new PositionInMaze(position.getXpos() + dx, position.getYpos() + dy));
                if (moveSuccessful) {
                    position = new PositionInMaze(position.getXpos() + dx, position.getYpos() + dy);
                    mazePane.setPlayerPosition(position);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            super.onGameReady(gameServer, player);

            players = gameServer.getPlayerMap();

            position = player.getPosition();

            //Run UI operations on the UI thread
            Platform.runLater(() -> {
                try {
                    mazePane = new MazePane(getMaze(), players);
                    mazePane.setPlayerPosition(position);

                    mazePane.prefHeightProperty().bind(stage.heightProperty());
                    mazePane.prefWidthProperty().bind(stage.widthProperty());
                    mazePane.setVisible(true);

                    Scene scene = new Scene(mazePane);
                    scene.setOnKeyPressed(event -> {
                        switch (event.getCode()) {
                            case UP:
                                movePlayer(0, -1);
                                break;
                            case DOWN:
                                movePlayer(0, 1);
                                break;
                            case LEFT:
                                movePlayer(-1, 0);
                                break;
                            case RIGHT:
                                movePlayer(1, 0);
                                break;
                        }
                    });

                    stage.setScene(scene);
                    stage.minWidthProperty().set(getMaze().length * 10);
                    stage.minHeightProperty().set(getMaze().length * 10 + 30);

                    stage.show();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
        }

        @Override
        public void onPositionStateChange(PositionInMaze position, boolean occupied) throws RemoteException {
            if (players != null) {
                if (occupied) {
                    players[position.getXpos()][position.getYpos()] = 1;
                } else {
                    players[position.getXpos()][position.getYpos()] = 0;
                }
                mazePane.repaintPositions();
            }
        }

    }

}
