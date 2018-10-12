package mazeoblig;

import client.Client;
import client.IUser;
import simulator.PositionInMaze;

import javax.swing.*;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameServer extends UnicastRemoteObject implements IGameServer {

    private static final int LEASE_SCHEDULER_THREADS = 4; //The amount of threads the lease scheduler should use
    private static final int LEASE_DURATION = 60; //How many seconds a lease is valid for

    private BoxMaze maze; //The maze that the server uses
    private Box[][] boxMaze; //The Box[][] representation of the maze that the server uses, only for internal use
    private int[][] playerMap; //The map of how many players are in any (x, y) point in the maze, same size as {@link #boxMaze}

    private Map<IUser, Player> users; //A map that maps a IUser to their corresponding Player instance

    private ScheduledThreadPoolExecutor leaseScheduler; //The executor that schedules lease expriry
    private Executor taskExecutor; //An executor used for varius async tasks
    private Executor tickExecutor; //The executor used to run the servers tick {@link GameServer#tick}

    private int[][] previousMap; //A copy of {@link #playerMap} from the previous tick, used to track changes

    /**
     * Constructs a new GameServer with a specific tickrate that decides how many timer per second the server should
     * update its users about changes since the last tick
     *
     * @param tick how many timer per second to update users
     * @throws RemoteException
     */
    protected GameServer(int tick) throws RemoteException {
        super();

        users = new ConcurrentHashMap<>();

        maze = new BoxMaze();
        boxMaze = maze.getMaze();
        playerMap = new int[maze.getMaze().length][maze.getMaze().length];
        previousMap = new int[maze.getMaze().length][maze.getMaze().length];

        leaseScheduler = new ScheduledThreadPoolExecutor(LEASE_SCHEDULER_THREADS);
        taskExecutor = Executors.newCachedThreadPool();
        tickExecutor = Executors.newCachedThreadPool();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                tickExecutor.execute(() -> tick());
            }
        }, 1000 / tick, 1000/tick);
    }

    /**
     * Compares {@link #playerMap} with {@link #previousMap} and updates all clients about the difference. Then it
     * copies {@link #playerMap} into {@link #previousMap} in preperation for the next time this method is called.
     *
     * It uses a list of {@link PositionChange} objects to notify the clients about changes.
     */
    private void tick() {
        int[][] mapState = Arrays.stream(playerMap).map(int[]::clone).toArray(int[][]::new);

        //The size of this list will never exceed its initial capacity
        List<PositionChange> delta = new ArrayList<>(mapState.length * mapState.length);

        for (int x = 0; x < mapState.length; x++) {
            for (int y = 0; y < mapState[x].length; y++) {
                int diff = mapState[x][y] - previousMap[x][y]; //Calculate the difference
                if (diff != 0) {
                    delta.add(new PositionChange(x, y, diff)); //If there is a difference, add it to the list
                }
            }
        }
        previousMap = mapState;

        if (delta.size() > 0) { //Dont bother sending an empty list
            broadcastMapStateChanged(delta);
        }
    }

    @Override
    public void register(IUser user) throws RemoteException {
        if (users.containsKey(user)) {
            System.out.println("A user tried to register twice");
            user.onGameReady(this, users.get(user));
        } else {
            new Lease(user, LEASE_DURATION);

            Player player = new Player(getRandomStartPosition());
            System.out.printf("New player connected, placing them at (%d, %d)\n", player.getPosition().getXpos(), player.getPosition().getYpos());

            users.put(user, player);

            user.onGameReady(this, player);

            player.setPosition(player.getPosition());
        }
    }

    private void onUserDisconnected(IUser user) {
        System.out.println("A user has disconnected");

        if (users.containsKey(user)) {
            Player player = users.remove(user);
            player.purge();
        }
    }

    private PositionInMaze getRandomStartPosition() {
        Random rand = new Random();
        return new PositionInMaze(rand.nextInt(Maze.DIM - 2) + 1, rand.nextInt(Maze.DIM - 2) + 1);
    }

    public void broadcastMapStateChanged(List<PositionChange> change) {
        for (IUser user : users.keySet()) {
            try {
                user.onPositionStateChange(change);
            } catch (RemoteException e) {
                taskExecutor.execute(() -> onUserDisconnected(user));
            }
        }
    }

    @Override
    public BoxMazeInterface getMaze() {
        return maze;
    }

    @Override
    public int[][] getPlayerMap() throws RemoteException {
        return playerMap;
    }

    private class Player extends UnicastRemoteObject implements IPlayer {

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
            if (position.getYpos() < 0 || position.getXpos() < 0 || position.getXpos() > boxMaze.length - 1 || position.getYpos() > boxMaze.length - 1) {
                return false;
            }

            int deltaX = this.position.getXpos() - position.getXpos();
            int deltaY = this.position.getYpos() - position.getYpos();
            if (Math.abs(deltaX) + Math.abs(deltaY) > 1) {
                return false;
            }

            Box box = boxMaze[this.position.getXpos()][this.position.getYpos()];
            if (deltaX == -1 && box.getRight() == null) {
                return false;
            } else if (deltaX == 1 && box.getLeft() == null) {
                return false;
            } else if (deltaY == -1 && box.getDown() == null) {
                return false;
            } else if (deltaY == 1 && box.getUp() == null) {
                return false;
            }

            setPosition(position);
            return true;
        }

        public void setPosition(PositionInMaze position) {
            playerMap[this.position.getXpos()][this.position.getYpos()]--;
            playerMap[position.getXpos()][position.getYpos()]++;

            this.position = position;
        }

        public void purge() {
            playerMap[this.position.getXpos()][this.position.getYpos()]--;
        }
    }

    private class Lease {

        private IUser user;
        private long duration;

        public Lease(IUser user, int duration) {
            this.user = user;
            this.duration = duration;

            leaseScheduler.schedule(this::exipre, duration, TimeUnit.SECONDS);
        }

        private void exipre() {
            try {
                boolean renew = user.onLeaseExpired();
                if (renew) {
                    renew();
                } else {
                    release();
                }
            } catch (RemoteException e) {
                release();
            }
        }

        public void renew() {
            leaseScheduler.schedule(this::exipre, duration, TimeUnit.SECONDS);
        }

        public void release() {
            onUserDisconnected(user);
        }

    }

}
