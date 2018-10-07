package client;

import mazeoblig.Box;
import mazeoblig.IGameServer;
import mazeoblig.INode;
import mazeoblig.IPlayer;
import simulator.PositionInMaze;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public abstract class User extends UnicastRemoteObject implements IUser {

    private IGameServer gameServer;
    private IPlayer player;

    private Box[][] maze;

    protected User() throws RemoteException {

    }

    @Override
    public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
        this.gameServer = gameServer;
        this.player = player;

        this.maze = gameServer.getMaze().getMaze();
    }

    @Override
    public void onPlayerConnected(IPlayer player) throws RemoteException {

    }

    @Override
    public void onPlayerDisconnected(IPlayer player) throws RemoteException {

    }

    @Override
    public boolean onLeaseExpired() throws RemoteException {
        return true;
    }

    @Override
    public INode requestNode() throws RemoteException {
        return new Node(this);
    }

    public Box[][] getMaze() throws RemoteException {
        return maze;
    }

    public IGameServer getGameServer() throws RemoteException {
        return gameServer;
    }

    public IPlayer getPlayer() throws RemoteException {
        return player;
    }

}
