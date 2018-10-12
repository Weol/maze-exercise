package mazeoblig;

import client.IUser;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGameServer extends Remote {

    /**
     * Should return the a refernece to the maze that this server uses.
     *
     * @return a reference to the maze that the server uses
     * @throws RemoteException
     */
    BoxMazeInterface getMaze() throws RemoteException;

    /**
     * Should return a representation of how many players are position within all positions in the maze that this server
     * uses
     *
     * @return a representation of how many players are position within all positions in the maze
     * @throws RemoteException
     */
    int[][] getPlayerMap() throws RemoteException;

    /**
     * Should register a user and call {@link IUser#onGameReady} when the users {@link IPlayer} instance is ready
     *
     * @param user the user to register
     * @throws RemoteException
     */
    void register(IUser user) throws RemoteException;

}
