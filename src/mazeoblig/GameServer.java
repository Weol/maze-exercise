package mazeoblig;

import client.IUser;
import simulator.PositionInMaze;

import java.io.Serializable;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class GameServer extends UnicastRemoteObject implements IGameServer {

    private static final int NOTIFICATION_SERVICE_THREADS = 4;

    private BoxMaze maze;

    private Map<IUser, IPlayer> players;
    private Executor notificationService;
    private ThreadPoolExecutor executor;
    private Map<IPlayer, PositionInMaze> pendingPositionChanges;
    private IUser[] users;

    protected GameServer(int tickrate) throws RemoteException {
        super();
        players = new ConcurrentHashMap<>();
        notificationService = Executors.newFixedThreadPool(NOTIFICATION_SERVICE_THREADS);
        maze = new BoxMaze(Maze.DIM);
        pendingPositionChanges = new Hashtable<>();
        users = new IUser[0];
        executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println(executor.getQueue().size());
            }
        },1000, 1000);
    }

    public void onUserConnected(IUser user) throws RemoteException {
        if (!players.containsKey(user)) {
            IPlayer player = new Player(new PositionInMaze(0, 0));
            players.put(user, player);

            broadcastPlayerConnected(player);

            System.out.println("New client has connected");

            user.onGameReady(this, player);
        }
    }

    public void onUserDisconnected(IUser user) {
        if (players.containsKey(user)) {
            IPlayer player = players.get(user);
            players.remove(user);

            System.out.println("Client has disconnected");

            broadcastPlayerDisconnected(player);
        }
    }

    public void broadcastPlayerConnected(IPlayer player) {
        notificationService.execute(() -> {
            for (IUser user : players.keySet())
                try {
                    user.onPlayerConnected(player);
                } catch (RemoteException e) {
                    onUserDisconnected(user);
                    e.printStackTrace();
                }
        });
    }

    public void broadcastPlayerDisconnected(IPlayer player) {
        notificationService.execute(() -> {
            for (IUser user : players.keySet()) {
                try {
                    user.onPlayerDisconnected(player);
                } catch (RemoteException e) {
                    onUserDisconnected(user);
                    e.printStackTrace();
                }
            }
        });
    }

    public void onPlayerPositionChanged(IPlayer player, PositionInMaze newPosition) {
        executor.execute(() -> {
            for (IUser user : players.keySet()) {
                try {
                    user.onPlayerPositionChange(player, newPosition);
                } catch (RemoteException e) {
                    onUserDisconnected(user);
                    //e.printStackTrace();
                }
            }
        });
    }

    @Override
    public BoxMazeInterface getMaze() {
        return maze;
    }

    @Override
    public IPlayer[] getPlayers() {
        return players.values().toArray(new IPlayer[players.size()]);
    }

    public class Player extends UnicastRemoteObject implements IPlayer {

        private PositionInMaze position;

        public Player(PositionInMaze startPosition) throws RemoteException {
            super();
            position = startPosition;
        }

        @Override
        public PositionInMaze getPosition() throws RemoteException {
            return position;
        }

        @Override
        public boolean moveTo(PositionInMaze position) throws RemoteException {
            this.position = position;
            onPlayerPositionChanged(this, position);
            return true;
        }

        public void setPosition(PositionInMaze position) {
            this.position = position;
        }

    }

}
