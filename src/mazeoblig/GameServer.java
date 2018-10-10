package mazeoblig;

import client.Client;
import client.IUser;
import simulator.PositionInMaze;

import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GameServer extends UnicastRemoteObject implements IGameServer {

    private static final int LEASE_SCHEDULER_THREADS = 4;
    private static final int LEASE_DURATION = 60;

    private static final int TASK_EXECUTOR_THREADS = 10;

    private static final int TICK_SCHEDULER_THREADS = 4;


    private BoxMaze maze;
    private int[][] playerMap;

    private Map<IUser, Player> users;

    private ScheduledThreadPoolExecutor leaseScheduler;
    private Executor taskExecutor;
    private ScheduledThreadPoolExecutor tickScheduler;

    private int[][] previousMap;

    protected GameServer(int tick) throws RemoteException {
        super();

        users = new ConcurrentHashMap<>();

        maze = new BoxMaze();
        playerMap = new int[maze.getMaze().length][maze.getMaze().length];
        previousMap = new int[maze.getMaze().length][maze.getMaze().length];

        leaseScheduler = new ScheduledThreadPoolExecutor(LEASE_SCHEDULER_THREADS);
        tickScheduler = new ScheduledThreadPoolExecutor(TICK_SCHEDULER_THREADS);
        taskExecutor = Executors.newFixedThreadPool(TASK_EXECUTOR_THREADS);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                taskExecutor.execute(() -> tick());
            }
        }, 1000 / tick, 1000/tick);
    }

    private void tick() {
        int[][] mapState = Arrays.stream(playerMap).map(int[]::clone).toArray(int[][]::new);

        List<PositionChange> delta = new ArrayList<>(mapState.length * mapState.length);
        for (int x = 0; x < mapState.length; x++) {
            for (int y = 0; y < mapState[x].length; y++) {
                int diff = mapState[x][y] - previousMap[x][y];
                if (diff != 0) {
                    delta.add(new PositionChange(x, y, diff));
                }
            }
        }
        previousMap = mapState;

        broadcastMapStateChanged(delta);
    }

    @Override
    public void register(IUser user) throws RemoteException {
        if (users.containsKey(user)) {
            System.out.println("A user tried to register twice");
            user.onGameReady(this, users.get(user));
        } else {
            Lease lease = new Lease(user, LEASE_DURATION);

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
                e.printStackTrace();
                new Thread(() -> onUserDisconnected(user)).start();
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
            setPosition(position);
            return true;
        }

        public void setPosition(PositionInMaze position) {
            playerMap[this.position.getXpos()][this.position.getYpos()]--;
            /**
            if (playerMap[this.position.getXpos()][this.position.getYpos()] < 1) {
                broadcastMapStateChanged(this.position, false);
            }


            if (playerMap[position.getXpos()][position.getYpos()] < 1) {
                broadcastMapStateChanged(position, true);
            }
             **/
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
