package client;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;
import mazeoblig.*;
import simulator.PositionInMaze;
import simulator.VirtualUser;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Timer;
import java.util.TimerTask;

public class SimulateUsers {

    public static void main(String[] args) throws RemoteException, NotBoundException, InterruptedException {
        Registry registry = LocateRegistry.getRegistry(RMIServer.getHostName(), RMIServer.getRMIPort());
        IUserRegistry userRegistry = (IUserRegistry) registry.lookup(RMIServer.UserRegistryName);

        for (int i = 0; i < 1000; i++) {
            userRegistry.register(new User());
        }
    }

    public static class User extends UnicastRemoteObject implements IUser {

        protected User() throws RemoteException {

        }

        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            Box[][] boxMaze = gameServer.getMaze().getMaze();

            VirtualUser virtualUser = new VirtualUser(boxMaze);
            new Thread(() -> {
                for (PositionInMaze positionInMaze : virtualUser.getFirstIterationLoop()) {
                    try {
                        player.moveTo(positionInMaze);
                        Thread.sleep(100);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                while (true) {
                    for (PositionInMaze positionInMaze : virtualUser.getIterationLoop()) {
                        try {
                            player.moveTo(positionInMaze);
                            Thread.sleep(100);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }

        @Override
        public void onPlayerConnected(IPlayer player) throws RemoteException {
        }

        @Override
        public void onPlayerDisconnected(IPlayer player) throws RemoteException {
        }

        @Override
        public boolean onLeaseExpired() throws RemoteException {
            return true;
        }

        @Override
        public void onPlayerPositionsChange(IPlayer player) throws RemoteException {

        }

    }

}
