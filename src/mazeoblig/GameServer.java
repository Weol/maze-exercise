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
    private int[][] playerMap; //Map of how many players are in any (x, y) point in the maze, same size as {@link #boxMaze}
    private int[][] previousMap; //A copy of {@link #playerMap} from the previous tick, used to track changes

    private Map<IUser, Player> users; //A map that maps a IUser to their corresponding Player instance

    private ScheduledThreadPoolExecutor leaseScheduler; //The executor that schedules lease expriry
    private Executor taskExecutor; //An executor used for varius async tasks
    private Executor tickExecutor; //The executor used to run the servers tick {@link GameServer#tick}


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

        //Get a hash of the mapState
        int hashCode = Arrays.deepHashCode(mapState);

        //The size of this will never exceed its initial capacity
        PositionChange changes = new PositionChange(mapState.length * mapState.length, hashCode);
        for (int x = 0; x < mapState.length; x++) {
            for (int y = 0; y < mapState[x].length; y++) {
                int diff = mapState[x][y] - previousMap[x][y]; //Calculate the difference
                if (diff != 0) {
                    changes.add(x, y, diff); //If there is a difference, add it
                }
            }
        }
        previousMap = mapState;

        if (changes.size() > 0) { //Dont bother sending an empty list
            broadcastMapStateChanged(changes);
        }
    }

    /**
     * Registers a {@link IUser} and assigns them a new {@link IPlayer} that is placed in a random position in the maze.
     * A new Lease is also created for them. When the created Player is ready then {@link IUser#onGameReady} will be
     * called, and {@link Player#setPosition} will also be called, so that any other users get notified about this new
     * Players position.
     *
     * If this GameServer already has an entry for them then this method will simply call {@link IUser#onGameReady}
     * with the Player that is already assigned to them.
     *
     * @param user the remote IUser to register
     * @throws RemoteException
     */
    @Override
    public void register(IUser user) throws RemoteException {
        if (users.containsKey(user)) {
            System.out.println("A user tried to register twice");
            user.onGameReady(this, users.get(user));
        } else {
            //The lease is a inner class that schedules itself, so we don't need to keep track of it
            new Lease(user, LEASE_DURATION);

            Player player = new Player(getRandomStartPosition());
            System.out.printf("New player connected, placing them at (%d, %d)\n", player.getPosition().getXpos(), player.getPosition().getYpos());

            users.put(user, player);

            user.onGameReady(this, player);

            player.setPosition(player.getPosition());
        }
    }

    /**
     * This method is used to de-register a {@link IUser}. It also calls {@link Player#purge} to remove the players
     * position from {@link #playerMap} and notify other uses about this change.
     *
     * @param user the user that has disconnected
     */
    private void onUserDisconnected(IUser user) {
        System.out.println("A user has disconnected");

        if (users.containsKey(user)) {
            Player player = users.remove(user);
            player.purge();
        }
    }

    /**
     * Creates and returns a random PositionInMaze that is within the bounds of {@link #maze}
     *
     * @return a random position within {@link #maze}
     */
    private PositionInMaze getRandomStartPosition() {
        Random rand = new Random();
        return new PositionInMaze(rand.nextInt(Maze.DIM - 2) + 1, rand.nextInt(Maze.DIM - 2) + 1);
    }

    /**
     * Notifies all users that the map state has changed. It does this by calling {@link IUser#onPositionStateChange} on
     * all users. If a user cannot be reached for any reason then {@link #onUserDisconnected} will be called to
     * disconnect them. The disconnect is executed by {@link #taskExecutor} so that it will not slow down the broadcast
     * for the remaining users.
     *
     * @param change the changes since last tick
     */
    public void broadcastMapStateChanged(PositionChange change) {
        for (IUser user : users.keySet()) {
            try {
                user.onPositionStateChange(change);
            } catch (RemoteException e) {
                taskExecutor.execute(() -> users.remove(user));
            }
        }
    }

    /**
     * @return the maze that this server is using
     */
    @Override
    public BoxMazeInterface getMaze() {
        return maze;
    }

    /**
     * @return A 2-dimensional array that represents how many players are located within each position within the maze
     */
    @Override
    public int[][] getPlayerMap() throws RemoteException {
        return playerMap;
    }

    /**
     * This inner class is the servers implementation of {@link IPlayer}. Users use their reference to their instance of
     * this class to move their player and get their players position within the maze.
     */
    private class Player extends UnicastRemoteObject implements IPlayer {

        private PositionInMaze position; //The players position within the maze

        /**
         * Constructs a new player and sets their initial position
         *
         * @param startPosition the initial position of the player
         * @throws RemoteException
         */
        public Player(PositionInMaze startPosition) throws RemoteException {
            super();
            position = startPosition;
        }

        /**
         * @return the position of this player
         * @throws RemoteException
         */
        @Override
        public PositionInMaze getPosition() throws RemoteException {
            return position;
        }

        /**
         * Request this player to move from its current position to another position. If the player is allowed to move
         * to the position then the player will be moved and {@link #playerMap} will be modified accordingly.
         *
         * The movement is considered valid if it does not violate any of the following conditions:
         *  1: The position is outside the bounds of {@link #playerMap}
         *  2: The sum of the difference between the x and y values of the position and {@link #position} is greater than 1
         *  3: There is a wall between the position and {@link #position}
         *
         *  If the move request is successful and the player is moved then this method will return true, otherwise it
         *  will return false
         *
         * @param position the position to move to
         * @return whether or not the movement was successful (valid)
         * @throws RemoteException
         */
        @Override
        public boolean moveTo(PositionInMaze position) throws RemoteException {
            //Make sure position is not out of bounds
            if (position.getYpos() < 0 || position.getXpos() < 0 || position.getXpos() > boxMaze.length - 1 || position.getYpos() > boxMaze.length - 1) {
                return false;
            }

            int deltaX = this.position.getXpos() - position.getXpos();
            int deltaY = this.position.getYpos() - position.getYpos();

            //Make sure that the position is not further away than 1 box in x and y direction
            if (Math.abs(deltaX) + Math.abs(deltaY) > 1) {
                return false;
            }

            Box box = boxMaze[this.position.getXpos()][this.position.getYpos()];

            //Make sure that there is no wall in the direction we want to move
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

        /**
         * Sets the position of this player and modifies {@link #playerMap} accordingly
         * @param position
         */
        public void setPosition(PositionInMaze position) {
            playerMap[this.position.getXpos()][this.position.getYpos()]--;
            playerMap[position.getXpos()][position.getYpos()]++;

            this.position = position;
        }

        /**
         * Modifies {@link #playerMap} so that this player no longer is represented withing {@link #playerMap}
         */
        public void purge() {
            playerMap[this.position.getXpos()][this.position.getYpos()]--;
        }
    }

    /**
     * This class represents the lease of users, it uses {@link #leaseScheduler} to time the expiry of itself. It calls
     * {@link IUser#onLeaseExpired()} to notify the users that the lease has expired, if the user returns true then the
     * lease is renewed. If the user return false or if the lease cannot reach the user for any reason then the lease
     * will call {@link #onUserDisconnected} to disconnect the user.
     */
    private class Lease {

        private IUser user; //The user for whom the lease belongs
        private long duration; //How long between lease expiration

        /**
         * Constructs a new lease and schedules its expiry
         *
         * @param user The user for whom the lease belongs
         * @param duration How long between lease expiration
         */
        public Lease(IUser user, int duration) {
            this.user = user;
            this.duration = duration;

            leaseScheduler.schedule(this::exipre, (long) (duration + duration*Math.random()), TimeUnit.SECONDS);
        }

        /**
         * Called by {@link #leaseScheduler} when a lease has expired, it calls {@link IUser#onLeaseExpired()} and
         * renews the lease with {@link #renew()} if it returned true. If it returned false or if it could not reach the user for any reason
         * it will not renew itself and call {@link #release()} to release this lease.
         */
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

        /**
         * Schedules the next expiry of this lease
         */
        public void renew() {
            leaseScheduler.schedule(this::exipre, duration, TimeUnit.SECONDS);
        }

        /**
         * Releases this lease by not renewing it and calls {@link #onUserDisconnected} to disconnect {@link #user}
         */
        public void release() {
            //onUserDisconnected(user);
        }

    }

}
