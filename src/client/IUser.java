package client;

import mazeoblig.IGameServer;
import mazeoblig.IPlayer;
import simulator.PositionInMaze;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IUser extends Remote {

    void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException;

    void onPositionStateChange(PositionInMaze position, boolean occupied) throws RemoteException;

    boolean onLeaseExpired() throws RemoteException;

}
