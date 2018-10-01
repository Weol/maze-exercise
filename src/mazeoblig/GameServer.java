package mazeoblig;

import client.IUser;
import javafx.geometry.Pos;
import simulator.PositionInMaze;
import sun.misc.Queue;

import javax.swing.text.Position;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class GameServer extends UnicastRemoteObject implements IGameServer {

    private static final int NOTIFICATION_SERVICE_THREADS = 4;

    private BoxMaze maze;

    private Hashtable<IUser, IPlayer> players;
    private Executor notificationService;
    private Set<PositionUpdate> pendingPositionChanges;

    protected GameServer(int tickrate) throws RemoteException {
        super();
        players = new Hashtable<>();
        notificationService = Executors.newFixedThreadPool(NOTIFICATION_SERVICE_THREADS);
        maze = new BoxMaze(Maze.DIM);
    }

    public void onUserConnected(IUser user) throws RemoteException {
        IPlayer player = new Player(new PositionInMaze(0, 0));
        players.put(user, player);

        broadcastPlayerConnected(player);

        user.onGameReady(this, player);
    }

    public void onUserDisconnected(IUser user) {
        IPlayer player = players.get(user);
        players.remove(user);

        broadcastPlayerDisconnected(player);
    }

    public void broadcastPlayerConnected(IPlayer player) {
        notificationService.execute(() -> players.keySet().forEach(user -> {
            try {
                user.onPlayerConnected(player);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }));
    }

    public void broadcastPlayerDisconnected(IPlayer player) {
        notificationService.execute(() -> players.keySet().forEach(user -> {
            try {
                user.onPlayerDisconnected(player);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }));
    }

    public void onPlayerPositionChanged(IPlayer player, PositionInMaze newPosition) {
        notificationService.execute(() -> players.keySet().forEach(user -> {
            try {
                user.onPlayerPositionsChange(player);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }));
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

    public class PositionUpdate implements Serializable {

        private IPlayer player;
        private PositionInMaze position;

        public PositionUpdate(IPlayer player, PositionInMaze position) {
            this.player = player;
            this.position = position;
        }

        public IPlayer getPlayer() {
            return player;
        }

        public PositionInMaze getPosition() {
            return position;
        }

    }
}
