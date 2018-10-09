package mazeoblig;

import client.IUser;
import simulator.PositionInMaze;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class GameServer extends UnicastRemoteObject implements IGameServer {

    private static final int LEASE_SCHEDULER_THREADS = 4;

    private BoxMaze maze;
    private int[][] playerMap;

    private Hashtable<IUser, Player> users;

    private ScheduledThreadPoolExecutor leaseScheduler;

    protected GameServer() throws RemoteException {
        super();

        maze = new BoxMaze();
        playerMap = new int[maze.getMaze().length][maze.getMaze().length];

        leaseScheduler = new ScheduledThreadPoolExecutor(LEASE_SCHEDULER_THREADS);
    }

    @Override
    public void register(IUser user) throws RemoteException {
        if (users.containsKey(user)) {

        } else {
            Player player = new Player(getRandomStartPosition());

            user.onGameReady(this, player);

            player.setPosition(player.getPosition());
        }
    }

    private PositionInMaze getRandomStartPosition() {
        Random rand = new Random();
        return new PositionInMaze(rand.nextInt(Maze.DIM - 2) + 1, rand.nextInt(Maze.DIM - 2) + 1);
    }

    public void broadcastMapStateChanged(PositionInMaze position, boolean occupied) {
        for (INode node : nodes) {
            try {
                node.onPositionStateChange(position, occupied);
            } catch (RemoteException e) {
                onNodeDisconnected(node);
                e.printStackTrace();
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
            setPosition(position);
            return true;
        }

        public void setPosition(PositionInMaze position) {
            playerMap[this.position.getXpos()][this.position.getYpos()]--;
            if (playerMap[this.position.getXpos()][this.position.getYpos()] < 1) {
                broadcastMapStateChanged(this.position, false);
            }

            if (playerMap[position.getXpos()][position.getYpos()] < 1) {
                broadcastMapStateChanged(position, true);
            }
            playerMap[position.getXpos()][position.getYpos()]++;

            this.position = position;
        }

    }

}
