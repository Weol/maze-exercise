package client;

import mazeoblig.BoxMazeInterface;
import mazeoblig.IGameServer;
import mazeoblig.INode;
import mazeoblig.IPlayer;
import simulator.PositionInMaze;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Node extends UnicastRemoteObject implements INode {

    private IUser user;

    private Set<IUser> users;

    private IGameServer parentServer;

    public Node(IUser user) throws RemoteException {
        this.user = user;
        this.users = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
        parentServer = gameServer;
        user.onGameReady(gameServer, player);
    }

    @Override
    public void onPlayerConnected(IPlayer player) throws RemoteException {
        user.onPlayerConnected(player);

        Iterator<IUser> iterator = users.iterator();
        while (iterator.hasNext()) {
            IUser user = iterator.next();
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

        Iterator<IUser> iterator = users.iterator();
        while (iterator.hasNext()) {
            IUser user = iterator.next();
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
    public void onPlayerPositionChange(IPlayer player, PositionInMaze position) throws RemoteException {
        user.onPlayerPositionChange(player, position);
        Iterator<IUser> iterator = users.iterator();
        while (iterator.hasNext()) {
            IUser user = iterator.next();
            try {
                user.onPlayerPositionChange(player, position);
            } catch (RemoteException e) {
                e.printStackTrace();
                users.remove(user);
            }
        }
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
    public IPlayer[] getPlayers() throws RemoteException {
        return parentServer.getPlayers();
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
