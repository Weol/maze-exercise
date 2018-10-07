package mazeoblig;

import client.IUser;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGameServer extends Remote {

    BoxMazeInterface getMaze() throws RemoteException;

    IPlayer[] getPlayers() throws RemoteException;

    void onUserConnected(IUser user) throws RemoteException;

    void onUserDisconnected(IUser user) throws RemoteException;

}
