package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import mazeoblig.*;
import paramaters.FunctionFlag;
import paramaters.ParameterInterpretation;
import paramaters.ParameterInterpreter;
import simulator.PositionInMaze;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Client extends Application {

    private Stage stage; //The stage (window)
    private MazePane mazePane; //The pane that draws the maze and all the players

    //The map representing how many players are at all points of the maze of the server this client is connected to
    private int[][] players;

    //An identifier that identifies which MapChangeEvent this client last processed
    private long tickIndex;

    //The pending MapChangeEvents that have arrived out of order and is waiting for their index
    private SortedSet<MapChangeEvent> pendingChanges;

    //Parameters
    private static String host; //The address of the host
    private static String localhost; //The address of this client
    private static int port; //The port of the RMI registry
    private static int refreshRate; //The refresh rate (fps) of this client

    /**
     * Interprets the parameters and launches the JavaFX application
     */
    public static void main(String[] args) {
        ParameterInterpreter interpreter = new ParameterInterpreter(
                new FunctionFlag("host", "h", "The address of the server host", String::new),
                new FunctionFlag("port", "p", "The port of the host RMI registry", Integer::new),
                new FunctionFlag("localhost", "lh", "The outside facing ip of the local machine", String::new),
                new FunctionFlag("rate", "r", "The refresh rate of the rendering", Integer::new)
        );
        ParameterInterpretation intepretation = interpreter.intepret(args);

        host = intepretation.get("host", getLocalHostAddress()); //Set host to the host argument or local host
        localhost = intepretation.get("localhost", getLocalHostAddress()); //Set localhost to the localhost argument or local host
        port = intepretation.get("port", RMIServer.getRMIPort()); //Set port to the port argument or RMIServer.getRMIPort()
        refreshRate = intepretation.get("rate", 12); //Sets the refresh rate of the canvas to the rate argument or 12

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

    /**
     * Called by JavaFX to start the application (window)
     */
    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;

        pendingChanges = new TreeSet<>(Comparator.comparingLong(MapChangeEvent::getIndex));

        System.out.printf("Fetching registry at %s:%d\n", host, port);

        Registry registry = LocateRegistry.getRegistry(host, port);
        IGameServer gameServer = (IGameServer) registry.lookup(RMIServer.GameServerName);

        System.out.printf("Registering client\n");
        gameServer.register(new UserImpl());
    }

    /**
     * An implementation of {@link IUser} using the abstract class {@link User} for convenience
     */
    private class UserImpl extends User {

        private PositionInMaze position; //The position of the player that belongs to this client

        protected UserImpl() throws RemoteException {
        }

        /**
         * Attempts to move this client's player in a x and y direction, if them movement is not successful then
         * {@link #position} will not be changed. If it is then {@link #position} will be updated and
         * {@link MazePane#setPlayerPosition} will be called on {@link #mazePane} to update the players position.
         *
         * @param dx the x direction of the move
         * @param dy the y direction of the move
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

        /**
         * Called after {@link IGameServer#register} to signal that this clients player is ready.
         *
         * @param gameServer the gameserver
         * @param player this clients player
         * @throws RemoteException
         */
        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            super.onGameReady(gameServer, player);

            System.out.printf("Game is ready\n");

            System.out.printf("Fetching map of players\n");
            PlayerMap map = gameServer.getPlayerMap();
            tickIndex = map.getIndex(); //Sets the maps tickIndex so we can synchronize MapChangeEvents
            players = map.getMap();

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

                    //Exit application when window is closed
                    stage.setOnCloseRequest(t -> {
                        Platform.exit();
                        System.exit(0);
                    });

                    stage.show();

                    System.out.printf("Starting maze render at %d refresh rate\n", refreshRate);
                    ScheduledThreadPoolExecutor refreshExecutor = new ScheduledThreadPoolExecutor(1);

                    refreshExecutor.scheduleAtFixedRate(mazePane::repaintPositions, 0, 1000 / refreshRate, TimeUnit.MILLISECONDS);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
        }

        /**
         * Called by the game server to notify that one or more players have moved. It checks if the MapChangeEvents
         * index is equal to {@link #tickIndex} + 1, if it is then we iterate through the changes and change
         * {@link #players} accordingly, and increment {@link #tickIndex} in anticipation of the next call to this
         * method. After that we check if there are any pending MapChangeEvents that should be applied as well; if there
         * is a pending MapChangeEvent with index equal to {@link #tickIndex} + 1 after we incremented, then it means
         * that a MapChangeEvent was received out of order and was stored until its predecessor arrived.
         *
         * If the MapChangeEvent's index does not equal {@link #tickIndex} + 1, but is greater, then it means that the
         * MapChangeEvent arrived out of order and is stored in {@link #pendingChanges} until its time for it
         * to be applied.
         *
         * If the MapChangeEvent's index is less than {@link #tickIndex} + 1 then it means we've received the same
         * MapChangeEvent twice, or we have received a MapChangeEvent that precedes our initial fetch of the servers
         * player map.
         */
        @Override
        public synchronized void onPlayerMapChange(MapChangeEvent change) throws RemoteException {
            if (players != null) {
                if (change.getIndex() == tickIndex + 1) { //Check if the MapChangeEvent is the one we're waiting for
                    for (int i = 0; i < change.size(); i++) { //Loop through the changes
                        int[] entry = change.get(i);
                        players[entry[0]][entry[1]] += entry[2]; //Apply the change
                    }
                    tickIndex++;

                    if (pendingChanges.size() > 0) {
                        MapChangeEvent pending = pendingChanges.first();
                        if (pending.getIndex() == tickIndex + 1) { //Check if the pending change should be applied
                            pendingChanges.remove(pending);
                            onPlayerMapChange(pending); //Apply the pending change
                        }
                    }
                } else if (change.getIndex() > tickIndex + 1) { //Check if we need to save this change for later
                    pendingChanges.add(change);
                }
            }
        }

        /**
         * Called by the server to notify that this clients map is probably faulty. Empties {@link #pendingChanges} and
         * request a new map from the server.
         */
        @Override
        public void invalidateMap() throws RemoteException {
            players = null; //To stop {@link #onPlayerMapChange} to run while we fetch a the map

            pendingChanges.clear();
            PlayerMap map = getGameServer().getPlayerMap();
            tickIndex = map.getIndex();
            players = map.getMap();
        }

    }

}
