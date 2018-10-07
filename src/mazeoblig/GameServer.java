package mazeoblig;

import client.IUser;
import javafx.collections.transformation.SortedList;
import simulator.PositionInMaze;

import java.io.Serializable;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class GameServer extends UnicastRemoteObject implements IGameServer {

    private static final int PLAYERS_PER_NODE = 10;

    private BoxMaze maze;

    private Map<IUser, IPlayer> players;
    private Map<IUser, INode> nodes;
    private INode latestNode;

    private int[][] playerMap;

    protected GameServer(int tickrate) throws RemoteException {
        super();
        players = new ConcurrentHashMap<>();
        nodes = new ConcurrentHashMap<>();

        maze = new BoxMaze();
        playerMap = new int[maze.getMaze().length][maze.getMaze().length];
    }

    @Override
    public void onUserConnected(IUser user) throws RemoteException {
        if (!players.containsKey(user)) {
            IPlayer player = new Player(new PositionInMaze(0, 0));
            players.put(user, player);

            if (players.size() > nodes.size() * PLAYERS_PER_NODE) {
                System.out.println("Creating new node!");
                INode node = user.requestNode();
                if (node != null) {
                    nodes.put(user, node);
                } else {
                    throw new IllegalStateException("A client must accept the responsibility of a node!!!");
                }
                node.onGameReady(this, player);
                latestNode = node;
            } else {
                INode node = latestNode;
                players.put(user, player);
                node.onUserConnected(user);
                user.onGameReady(node, player);
            }

            broadcastPlayerConnected(player);

            System.out.println("New client has connected");
        }
    }

    @Override
    public void onUserDisconnected(IUser user) {
        if (players.containsKey(user)) {
            IPlayer player = players.get(user);
            players.remove(user);

            System.out.println("Client has disconnected");

            broadcastPlayerDisconnected(player);
        }

        if (nodes.containsKey(user)) {
            throw new IllegalStateException("SHIT! A NODE DISCONNECTED!");
        }
    }

    public void broadcastPlayerConnected(IPlayer player)  {
        for (INode node : nodes.values()) {
            try {
                node.onPlayerConnected(player);
            } catch (RemoteException e) {
                onUserDisconnected(node);
                e.printStackTrace();
            }
        }
    }

    public void broadcastPlayerDisconnected(IPlayer player) {
        for (INode node : nodes.values()) {
            try {
                node.onPlayerDisconnected(player);
            } catch (RemoteException e) {
                onUserDisconnected(node);
                e.printStackTrace();
            }
        }
    }

    public void onPlayerPositionChanged(PositionInMaze position, boolean occupied) {
        for (INode node : nodes.values()) {
            try {
                node.onPositionStateChange(position, occupied);
            } catch (RemoteException e) {
                onUserDisconnected(node);
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
                onPlayerPositionChanged(this.position, false);
            }

            if (playerMap[position.getXpos()][position.getYpos()] < 1) {
                onPlayerPositionChanged(position, true);
            }
            playerMap[position.getXpos()][position.getYpos()]++;

            this.position = position;

            return true;
        }

    }

}
