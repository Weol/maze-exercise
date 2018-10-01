package mazeoblig;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGameServer extends Remote {

    BoxMazeInterface getMaze() throws RemoteException;

    IPlayer[] getPlayers() throws RemoteException;

}
