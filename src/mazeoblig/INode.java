package mazeoblig;

import client.IUser;
import simulator.PositionInMaze;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface INode extends Remote {

    void acceptUser(IUser user) throws RemoteException;

    void onPositionStateChange(PositionInMaze position, boolean occupied) throws RemoteException;

}
