package client;

import mazeoblig.Box;
import mazeoblig.IGameServer;
import mazeoblig.IPlayer;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

/**
 * A simple abstract class to simplify the implementation of {@link IUser}
 */
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

    /**
     * We always want to renew our lease
     */
    @Override
    public boolean onLeaseExpired() throws RemoteException {
        return true;
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
