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

    private static final int LEASE_SCHEDULER_THREADS = 4;
    private static final int LEASE_DURATION = 60;

    private BoxMaze maze;
    private Box[][] boxMaze;
    private int[][] playerMap;

    private Map<IUser, Player> users;

    private ScheduledThreadPoolExecutor leaseScheduler;
    private ThreadPoolExecutor taskExecutor;
    private ThreadPoolExecutor tickExecutor;

    private int[][] previousMap;

    protected GameServer(int tick) throws RemoteException {
        super();

        users = new ConcurrentHashMap<>();

        maze = new BoxMaze();
        boxMaze = maze.getMaze();
        playerMap = new int[maze.getMaze().length][maze.getMaze().length];
        previousMap = new int[maze.getMaze().length][maze.getMaze().length];

        leaseScheduler = new ScheduledThreadPoolExecutor(LEASE_SCHEDULER_THREADS);
        taskExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        tickExecutor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                tickExecutor.execute(() -> tick());
            }
        }, 1000 / tick, 1000/tick);

        JFrame frame = new JFrame();
        JList<String> list = new JList<>();
        list.setSize(frame.getSize());
        frame.add(list);

        frame.setBounds(0, 0, 300, 300);
        frame.setAlwaysOnTop(true);

        frame.setVisible(true);

        Timer diagnosticTimer = new Timer();
        diagnosticTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                list.setListData(new String[] {
                        "Users connected: " + users.size(),
                        "Tick executor load: " + tickExecutor.getQueue().size(),
                        "Task executor load: " + taskExecutor.getQueue().size(),
                });
            }
        }, 1000 / tick, 1000 / tick);
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
