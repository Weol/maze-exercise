package client;

import mazeoblig.IGameServer;
import mazeoblig.IPlayer;
import mazeoblig.PositionChange;
import simulator.PositionInMaze;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IUser extends Remote {

    /**
     * Should be called by a {@link IGameServer} after {@link IGameServer#register} is called. It tells that it has
     * created a {@link IPlayer} that is ready to be used.
     *
     * @param gameServer the gameServer that the player belongs to
     * @param player the player
     * @throws RemoteException
     */
    void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException;

    /**
     * Should be called to notify this user that the map that represents the players of th
     *
     * @param change
     * @throws RemoteException
     */
    void onPositionStateChange(PositionChange change) throws RemoteException;

    /**
     * Should return true if this IUser wants to renew it lease, false otherwise.
     *
     * @return whether or not to renew the lease
     * @throws RemoteException
     */
    boolean onLeaseExpired() throws RemoteException;

}
