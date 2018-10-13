package mazeoblig;

import client.IUser;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGameServer extends Remote {

    /**
     * Should return the a refernece to the maze that this server uses.
     *
     * @return a reference to the maze that the server uses
     */
    BoxMazeInterface getMaze() throws RemoteException;

    /**
     * Should return a representation of how many players are position within all positions in the maze that this server
     * uses, and a index that represents which tick the map was last used by
     *
     * @return the last state of the map and its index
     */
    PlayerMap getPlayerMap() throws RemoteException;

    /**
     * Should register a user and call {@link IUser#onGameReady} when the users {@link IPlayer} instance is ready
     *
     * @param user the user to register
     */
    void register(IUser user) throws RemoteException;

    /**
     * Should disconnect a user
     */
    void disconnect(IUser user) throws RemoteException;

}
