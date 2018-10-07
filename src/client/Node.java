package client;

import mazeoblig.BoxMazeInterface;
import mazeoblig.IGameServer;
import mazeoblig.INode;
import mazeoblig.IPlayer;
import simulator.PositionInMaze;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Node extends UnicastRemoteObject implements INode {

    private IUser user;

    private CopyOnWriteArraySet<IUser> users;

    private IGameServer parentServer;

    private ThreadPoolExecutor executor;

    public Node(IUser user) throws RemoteException {
        this.user = user;
        this.users = new CopyOnWriteArraySet<>();

        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    }

    @Override
    public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
        parentServer = gameServer;
        user.onGameReady(gameServer, player);
    }

    @Override
    public void onPlayerConnected(IPlayer player) throws RemoteException {
        user.onPlayerConnected(player);

        for (IUser user : users) {
            try {
                user.onPlayerConnected(player);
            } catch (RemoteException e) {
                e.printStackTrace();
                users.remove(user);
            }
        }
    }

    @Override
    public void onPlayerDisconnected(IPlayer player) throws RemoteException {
        user.onPlayerDisconnected(player);

        for (IUser user : users) {
            try {
                user.onPlayerDisconnected(player);
            } catch (RemoteException e) {
                e.printStackTrace();
                users.remove(user);
            }
        }
    }

    @Override
    public boolean onLeaseExpired() throws RemoteException {
        return user.onLeaseExpired();
    }

    @Override
    public void onPositionStateChange(PositionInMaze position, boolean occupied) throws RemoteException {
        user.onPositionStateChange(position, occupied);

        executor.execute(() -> {
            for (IUser user : users) {
                try {
                    user.onPositionStateChange(position, occupied);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    users.remove(user);
                }
            }
        });
    }

    @Override
    public INode requestNode() {
        throw new IllegalStateException("requestNode should never be called on a node!");
    }

    @Override
    public BoxMazeInterface getMaze() throws RemoteException {
        return parentServer.getMaze();
    }

    @Override
    public int[][] getPlayerMap() throws RemoteException {
        return parentServer.getPlayerMap();
    }

    @Override
    public void onUserConnected(IUser user) throws RemoteException {
        users.add(user);
    }

    @Override
    public void onUserDisconnected(IUser user) throws RemoteException {
        users.remove(user);
    }

}
