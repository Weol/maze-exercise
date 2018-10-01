package client;

import mazeoblig.GameServer;
import mazeoblig.IGameServer;
import mazeoblig.IPlayer;
import simulator.PositionInMaze;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.concurrent.TimeUnit;

public interface IUser extends Remote {

    void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException;

    void onPlayerConnected(IPlayer player) throws RemoteException;

    void onPlayerDisconnected(IPlayer player) throws RemoteException;

    boolean onLeaseExpired() throws RemoteException;

    void onPlayerPositionsChange(IPlayer player) throws RemoteException;

}
