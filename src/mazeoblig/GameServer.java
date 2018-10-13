package mazeoblig;

import client.IUser;
import simulator.PositionInMaze;

import javax.swing.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.*;

public class GameServer extends UnicastRemoteObject implements IGameServer {

    enum Timeout {
        NOT_TIMED_OUT,
        TIMED_OUT,
        RECENTLY_TIMED_OUT
    }

    private static final int TIMEOUT_SCHEDULER_THREADS = 5; //How long to wait until we disconnect a non-responding user
    private static final int TIMEOUT_DELAY = 5; //How long to wait until we disconnect a non-responding user

    private static final int LEASE_SCHEDULER_THREADS = 4; //The amount of threads the lease scheduler should use
    private static final int LEASE_DURATION = 60; //How many seconds a lease is valid for

    private BoxMaze maze; //The maze that the server uses
    private Box[][] boxMaze; //The Box[][] representation of the maze that the server uses, only for internal use
    private int[][] playerMap; //Map of how many players are in any (x, y) point in the maze, same size as {@link #boxMaze}
    private int[][] previousMap; //A copy of {@link #playerMap} from the previous tick, used to track changes

    private Map<IUser, Player> users; //A map that maps a IUser to their corresponding Player instance

    private ScheduledThreadPoolExecutor leaseScheduler; //The executor that schedules lease expiry
    private ScheduledThreadPoolExecutor timeOutScheduler; //The executor that schedules timeouts
    private ThreadPoolExecutor taskExecutor; //An executor used for various async tasks

    private long tickIndex; //Represents an id of the last tick the server sent to its users

    /**
     * Constructs a new GameServer with a specific tick rate that decides how many timer per second the server should
     * update its users about changes since the last tick
     *
     * @param rate how many timer per second to update users
     */
    protected GameServer(int rate) throws RemoteException {
        super();

        users = new ConcurrentHashMap<>();

        maze = new BoxMaze();
        boxMaze = maze.getMaze();
        playerMap = new int[maze.getMaze().length][maze.getMaze().length];
        previousMap = new int[maze.getMaze().length][maze.getMaze().length];

        timeOutScheduler = new ScheduledThreadPoolExecutor(TIMEOUT_SCHEDULER_THREADS);
        leaseScheduler = new ScheduledThreadPoolExecutor(LEASE_SCHEDULER_THREADS);
        taskExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();


        Timer timer = new Timer();
        timer.schedule(new TimerTask() {

            private int skippedTicks = 0;

            @Override
            public void run() {
                if (taskExecutor.getActiveCount() < 1000) { //Skip a tick if the taskExecutor has alot of load
                    if (skippedTicks > 0) {
                        System.out.println("Skipped " + skippedTicks + " ticks");
                    }
                    taskExecutor.execute(() -> tick());
                    skippedTicks = 0;
                } else {
                    skippedTicks++;
                }
            }
        }, 0, 1000 / rate);
    }

    /**
     * Registers a {@link IUser} and assigns them a new {@link IPlayer} that is placed in a random position in the maze.
     * A new Lease is also created for them. When the created Player is ready then {@link IUser#onGameReady} will be
     * called.
     *
     * If this GameServer already has an entry for them then this method will simply call {@link IUser#onGameReady}
     * with the Player that is already assigned to them.
     *
     * @param user the remote IUser to register
     */
    @Override
    public synchronized void register(IUser user) throws RemoteException {
        if (users.containsKey(user)) {
            System.out.println("A user tried to register twice");
            user.onGameReady(this, users.get(user));
        } else {
            //The lease is a inner class that schedules itself, so we don't need to keep track of it
            new Lease(user, LEASE_DURATION);

            Player player = new Player(getRandomStartPosition());
            //System.out.printf("New player connected, placing them at (%d, %d)\n", player.getPosition().getXpos(), player.getPosition().getYpos());

            users.put(user, player);

            user.onGameReady(this, player);
        }
    }

    /**
     * This method is used to de-register a {@link IUser}. It also calls {@link Player#purge} to remove the players
     * position from {@link #playerMap}
     *
     * @param user the user that has (should be) disconnected
     */
    @Override
    public void disconnect(IUser user) {
        Player player = users.remove(user);

        if (player != null) {
            player.purge();
            System.out.println("A user has disconnected");
        }
    }

    /**
     * Compares {@link #playerMap} with {@link #previousMap} and updates all clients about the difference. Then it
     * copies {@link #playerMap} into {@link #previousMap} in preparation for the next time this method is called.
     *
     * This method uses {@link MapChangeEvent} to notify users about changes.
     *
     * If there was a difference, then {@link #tickIndex} will be incremented and passed with the {@link MapChangeEvent}
     * to the users so that they can synchronize MapChangeEvents if they arrive out of order.
     */
    private void tick() {
        int[][] mapState;
        synchronized (this) { //To make sure nothing changes while we copy the playerMap
            mapState = Arrays.stream(playerMap).map(int[]::clone).toArray(int[][]::new);
        }

        //The total amount of changes will never exceed the dimensions of the maze squared
        MapChangeEvent mapChangeEvent = new MapChangeEvent(mapState.length * mapState.length);
        for (int x = 0; x < mapState.length; x++) {
            for (int y = 0; y < mapState[x].length; y++) {
                int diff = mapState[x][y] - previousMap[x][y]; //Calculate the difference
                if (diff != 0) {
                    mapChangeEvent.add(x, y, diff); //If there is a difference, add it
                }
            }
        }
        previousMap = mapState;

        if (mapChangeEvent.size() > 0) { //Don't bother broadcasting if there wasn't not changes;
            synchronized (this) {
                tickIndex++;
            }

            mapChangeEvent.setIndex(tickIndex);
            broadcastPlayerMapChange(mapChangeEvent);
        }
    }

    /**
     * Creates and returns a random PositionInMaze that is within the bounds of {@link #maze}
     *
     * @return a random position within {@link #maze}
     */
    private PositionInMaze getRandomStartPosition() {
        Random rand = new Random();
        return new PositionInMaze(rand.nextInt(playerMap.length - 2) + 1, rand.nextInt(playerMap.length - 2) + 1);
    }

    /**
     * Loops through all users in {@link #users} and calls {@link IUser#onPlayerMapChange} on them. Each call
     * is ran with {@link #taskExecutor} so that if a user does not respond then it won't slow down for the rest of the
     * users.
     *
     * If a {@link RemoteException} is thrown then the players {@link Player#timeOut} will be set to
     * {@link Timeout#TIMED_OUT}, causing this method to ignore the user until their {@link Player#timeOut} is set to
     * {@link Timeout#RECENTLY_TIMED_OUT} by {@link #timeOutScheduler} after {@link #TIMEOUT_DELAY} seconds. If a
     * {@link RemoteException} is thrown again then that user will be disconnected by calling {@link #disconnect}. If a
     * {@link RemoteException} is not thrown then the player's {@link Player#timeOut} will be reset to
     * {@link Timeout#NOT_TIMED_OUT} and {@link IUser#invalidateMap()} is called on that user.
     *
     * @param change the changes since last tick
     */
    public void broadcastPlayerMapChange(MapChangeEvent change) {
        for (Map.Entry<IUser, Player> entry : users.entrySet()) {
            taskExecutor.execute(() -> {
                IUser user = entry.getKey();
                Player player = entry.getValue();

                if (player.timeOut != Timeout.TIMED_OUT) { //Ignore user if their player is timed out
                    try {
                        user.onPlayerMapChange(change);
                        if (player.timeOut != Timeout.NOT_TIMED_OUT) {
                            player.timeOut = Timeout.NOT_TIMED_OUT;  //Reset their time out
                            user.invalidateMap(); //If they have timed out and returned, then their map is probably all messed up
                        }
                    } catch (RemoteException e) {
                        if (player.timeOut == Timeout.NOT_TIMED_OUT) { //Check if this is their first time timing out
                            player.timeOut = Timeout.TIMED_OUT;
                            timeOutScheduler.schedule(() -> player.timeOut = Timeout.RECENTLY_TIMED_OUT, TIMEOUT_DELAY, TimeUnit.SECONDS); //Give a second chance
                        } else if (player.timeOut == Timeout.RECENTLY_TIMED_OUT) { //Check if this is their second chance
                            disconnect(user);
                        }
                    }
                }
            });
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
     * Returns a {@link PlayerMap} that contains {@link #previousMap} and {@link #tickIndex}. This is so that clients
     * can synchronize their map to the servers map by comparing the tickIndex that they receive and tickIndex that is
     * contained within the {@link MapChangeEvent} that broadcast changes to {@link PlayerMap}.
     *
     * @return the last version of the player map that was broadcasted by {@link #tick()}
     */
    @Override
    public PlayerMap getPlayerMap() throws RemoteException {
        return new PlayerMap(previousMap, tickIndex);
    }

    /**
     * This inner class is the servers implementation of {@link IPlayer}. Users use their reference to their instance of
     * this class to move their player and get their player's position within the maze.
     */
    private class Player extends UnicastRemoteObject implements IPlayer {

        private Timeout timeOut = GameServer.Timeout.NOT_TIMED_OUT; //The timeout status of the user that this player belongs to

        private PositionInMaze position; //The players position within the maze

        /**
         * Constructs a new player and sets their initial position and modifies {@link #playerMap} so that the newly
         * created player is represented within it.
         *
         * @param startPosition the initial position of the player
         */
        public Player(PositionInMaze startPosition) throws RemoteException {
            super();
            position = startPosition;
            synchronized (this) {
                playerMap[position.getXpos()][position.getYpos()]++;
            }
        }

        /**
         * @return the position of this player
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
         */
        @Override
        public boolean moveTo(PositionInMaze position) throws RemoteException {
            //Make sure position is not out of bounds
            if (position.getYpos() < 0 || position.getXpos() < 0 || position.getXpos() > boxMaze.length - 1 || position.getYpos() > boxMaze.length - 1) {
                return false;
            }

            int deltaX = this.position.getXpos() - position.getXpos();
            int deltaY = this.position.getYpos() - position.getYpos();

            //Make sure that the position is not further away than 1 box in x + y direction
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
         * Sets the position of this player and modifies {@link #playerMap} accordingly.
         */
        public void setPosition(PositionInMaze position) {
            if (position.getXpos() != this.position.getXpos() || position.getYpos() != this.position.getYpos()) {
                synchronized (GameServer.this) {
                    playerMap[this.position.getXpos()][this.position.getYpos()]--;
                    playerMap[position.getXpos()][position.getYpos()]++;
                }

                this.position = position;
            }
        }

        /**
         * Modifies {@link #playerMap} so that this player no longer is represented within {@link #playerMap}
         */
        public void purge() {
            synchronized (GameServer.this) {
                playerMap[position.getXpos()][position.getYpos()]--;
            }
        }
    }

    /**
     * This class represents the lease of a user, it uses {@link #leaseScheduler} to time the expiry of itself. It calls
     * {@link IUser#onLeaseExpired()} to notify the user that the lease has expired, if the method returns true then the
     * lease is renewed. If the method returns false or if the lease cannot reach the user for any reason then the lease
     * will call {@link Lease#release()} to release the lease and disconnect the user.
     */
    private class Lease {

        private IUser user; //The user for whom the lease belongs
        private long duration; //How long between lease expiration

        /**
         * Constructs a new lease and schedules its expiry.
         * The first expiry will expire sometime between the duration and the duration times two. This is done so that
         * if a large amount of leases are created at once they won't expire at once and overload the
         * {@link #leaseScheduler}.
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
         * Schedules the next expiry of this lease in {@link #duration} seconds
         */
        public void renew() {
            leaseScheduler.schedule(this::exipre, duration, TimeUnit.SECONDS);
        }

        /**
         * Releases this lease by not renewing it and calls {@link #disconnect} to disconnect the {@link #user}
         */
        public void release() {
            disconnect(user);
        }

    }

}
