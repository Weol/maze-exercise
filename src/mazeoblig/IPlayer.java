package mazeoblig;

import simulator.PositionInMaze;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IPlayer extends Remote {

    PositionInMaze getPosition() throws RemoteException;

    boolean moveTo(PositionInMaze position) throws RemoteException;

}
