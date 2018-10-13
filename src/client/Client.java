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
import javafx.stage.WindowEvent;
import mazeoblig.*;
import mazeoblig.Box;
import paramaters.FunctionFlag;
import paramaters.ParameterInterpretation;
import paramaters.ParameterInterpreter;
import simulator.PositionInMaze;
import simulator.VirtualUser;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Client extends Application {

    private Stage stage; //The stage (window)
    private MazePane mazePane; //The pane that draws the maze and all the players

    //The map representing how many players are at all points of the maze of the server this client is connected to
    private int[][] players;

    //Parameters
    private static String host; //The address of the host
    private static String localhost; //The address of this client
    private static int port; //The port of the RMI registry
    private static int refreshRate; //The refresh rate (fps) of this client

    public static void main(String[] args) {
        ParameterInterpreter interpreter = new ParameterInterpreter(
                new FunctionFlag("host", "h", "The address of the server host", String::new),
                new FunctionFlag("port", "p", "The port of the host RMI registry", Integer::new),
                new FunctionFlag("localhost", "lh", "The outside facing ip of the local machine", String::new),
                new FunctionFlag("rate", "r", "The refresh rate of the rendering", Integer::new)
        );
        ParameterInterpretation intepretation = interpreter.intepret(args);

        host = intepretation.get("host", getLocalHostAddress());
        localhost = intepretation.get("localhost", getLocalHostAddress());
        port = intepretation.get("port", RMIServer.getRMIPort());
        refreshRate = intepretation.get("rate", 30);

        System.out.println("Setting local address to " + localhost);
        System.setProperty("java.rmi.server.hostname", localhost);

        launch();
    }

    /**
     * @return the local address, or "localhost" if we failed
     */
    private static String getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return "localhost";
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;

        System.out.printf("Fetching registry at %s:%d\n", host, port);

        Registry registry = LocateRegistry.getRegistry(host, port);
        IGameServer gameServer = (IGameServer) registry.lookup(RMIServer.GameServerName);

        System.out.printf("Registering client\n");
        gameServer.register(new UserImpl());
    }

    private class UserImpl extends User {

        private PositionInMaze position; //The position of the player that belongs to this client

        protected UserImpl() throws RemoteException {
        }

        /**
         * Attempts to move
         *
         * @param dx
         * @param dy
         */
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

            System.out.printf("Game is ready\n");

            System.out.printf("Fetching map of players\n");
            players = gameServer.getPlayerMap();

            System.out.printf("Fetching local players position\n");
            position = player.getPosition();

            //Run UI operations on the UI thread
            Platform.runLater(() -> {
                try {
                    mazePane = new MazePane(getMaze(), players);
                    mazePane.setPlayerPosition(position);

                    mazePane.prefHeightProperty().bind(stage.heightProperty());
                    mazePane.prefWidthProperty().bind(stage.widthProperty());
                    mazePane.setVisible(true);

                    mazePane.repaintPositions();

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

                    stage.setOnCloseRequest(t -> {
                        Platform.exit();
                        System.exit(0);
                    });

                    stage.show();

                    System.out.printf("Starting maze render at %d refresh rate\n", refreshRate);
                    ScheduledThreadPoolExecutor refreshExecutor = new ScheduledThreadPoolExecutor(2);
                    refreshExecutor.scheduleAtFixedRate(mazePane::repaintPositions, 0, 1000 / refreshRate, TimeUnit.MILLISECONDS);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
        }

        /**
         *
         *
         * @param change
         * @throws RemoteException
         */
        @Override
        public void onPositionStateChange(PositionChange change) throws RemoteException {
            if (players != null) {
                for (int i = 0; i < change.size(); i++) {
                    int[] entry = change.get(i);
                    players[entry[0]][entry[1]] += entry[2];
                }
            }
        }

    }

}
