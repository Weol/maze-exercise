package client;

import mazeoblig.IGameServer;
import mazeoblig.IPlayer;
import mazeoblig.PositionChange;
import simulator.PositionInMaze;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IUser extends Remote {

    void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException;

    void onPositionStateChange(List<PositionChange> change) throws RemoteException;

    boolean onLeaseExpired() throws RemoteException;

}
