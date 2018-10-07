package client;

import mazeoblig.GameServer;
import mazeoblig.IGameServer;
import mazeoblig.INode;
import mazeoblig.IPlayer;
import simulator.PositionInMaze;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public interface IUser extends Remote {

    void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException;

    void onPositionStateChange(PositionInMaze position, boolean occupied) throws RemoteException;

    INode requestNode() throws RemoteException;

}
