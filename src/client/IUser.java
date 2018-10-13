package client;

import mazeoblig.IGameServer;
import mazeoblig.IPlayer;
import mazeoblig.MapChangeEvent;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IUser extends Remote {

    /**
     * Should be called by a {@link IGameServer} after {@link IGameServer#register} is called. It tells that it has
     * created a {@link IPlayer} that is ready to be used.
     *
     * @param gameServer the gameServer that the player belongs to
     * @param player the player
     */
    void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException;

    /**
     * Should be called to notify this user that the map that represents the players of the server has changed
     *
     * @param change the change
     */
    void onPlayerMapChange(MapChangeEvent change) throws RemoteException;

    /**
     * Should return true if this IUser wants to renew it lease, false otherwise.
     *
     * @return whether or not to renew the lease
     */
    boolean onLeaseExpired() throws RemoteException;

    /**
     * Should be called by the server to notify a user that their map probably is wrong
     */
    void invalidateMap() throws RemoteException;

}
