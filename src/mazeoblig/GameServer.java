package mazeoblig;

import client.IUser;
import simulator.PositionInMaze;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

public class GameServer extends UnicastRemoteObject implements IGameServer, INodeManager {

    private static final int PLAYERS_PER_NODE = 10;

    private BoxMaze maze;

    private HashSet<INode> nodes;
    private Deque<INode> availableNodes;

    private int[][] playerMap;

    protected GameServer(int tickrate) throws RemoteException {
        super();
        nodes = new HashSet<>();
        availableNodes = new ArrayDeque<>();

        maze = new BoxMaze();
        playerMap = new int[maze.getMaze().length][maze.getMaze().length];
    }

    @Override
    public void register(IUser user) throws RemoteException {
        IPlayer player = new Player(new PositionInMaze(0, 0));

        if (availableNodes.size() < 1) {
            INode node = user.requestNode();
            if (node != null) {
                nodes.add(node);
            } else {
                throw new IllegalStateException("A client must accept the responsibility of a node!!!");
            }
            availableNodes.addFirst(node);
            user.onGameReady(this, player);
        } else {
            INode node = availableNodes.pollLast();
            availableNodes.addFirst(node);
            node.acceptUser(user);
            user.onGameReady(this, player);
        }
    }

    public void onNodeDisconnected(INode node) {
       nodes.remove(node);
       availableNodes.removeFirstOccurrence(node);
    }

    @Override
    public void migrateUsers(IUser[] user) {

    }

    @Override
    public void notifyNodeUnavailable(INode node) {
        availableNodes.remove(node);
    }

    @Override
    public void notifyNodeAvailable(INode node) {
        availableNodes.addFirst(node);
    }

    public void broadcast(PositionInMaze position, boolean occupied) {
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
            playerMap[this.position.getXpos()][this.position.getYpos()]--;
            if (playerMap[this.position.getXpos()][this.position.getYpos()] < 1) {
                broadcast(this.position, false);
            }

            if (playerMap[position.getXpos()][position.getYpos()] < 1) {
                broadcast(position, true);
            }
            playerMap[position.getXpos()][position.getYpos()]++;

            this.position = position;

            return true;
        }

    }

}
