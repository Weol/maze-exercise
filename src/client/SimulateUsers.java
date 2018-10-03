package client;

import javafx.application.Platform;
import javafx.geometry.Pos;
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
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SimulateUsers {

    private static List<User> readyUsers = new ArrayList<>();

    public static void main(String[] args) throws RemoteException, NotBoundException, InterruptedException {
        Registry registry = LocateRegistry.getRegistry(RMIServer.getHostName(), RMIServer.getRMIPort());
        IUserRegistry userRegistry = (IUserRegistry) registry.lookup(RMIServer.UserRegistryName);

        int count = 500;

        for (int i = 0; i < count; i++) {
            userRegistry.register(new User());
        }

        int nThreads = 10;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(nThreads);

        int usersPerThread = count / nThreads;

        for (int i = 0; i < nThreads; i++) {
            List<User> subUsers = readyUsers.subList(i * usersPerThread, Math.min((i+1)*usersPerThread, readyUsers.size()));
            System.out.println(i * usersPerThread + " to " + Math.max((i+1)*usersPerThread, readyUsers.size()));
            executor.scheduleWithFixedDelay(() -> {
                for (User subUser : subUsers) {
                    if (subUser.moves.size() != 0) {
                        try {
                            subUser.player.moveTo(subUser.moves.poll());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    } else {
                        subUser.moves.addAll(subUser.round);
                    }
                }
            }, 1, 1, TimeUnit.SECONDS);
        }
    }

    public static class User extends UnicastRemoteObject implements IUser {

        private IPlayer player;
        private Deque<PositionInMaze> moves;
        private List<PositionInMaze> round;

        protected User() throws RemoteException {
            moves = new LinkedList<>();
            round = new ArrayList<>();
        }

        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            Box[][] boxMaze = gameServer.getMaze().getMaze();
            VirtualUser virtualUser = new VirtualUser(boxMaze);

            this.player = player;

            moves.addAll(Arrays.asList(virtualUser.getFirstIterationLoop()));
            round.addAll(Arrays.asList(virtualUser.getIterationLoop()));

            moves.addAll(round);

            readyUsers.add(this);
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
        public void onPlayerPositionChange(IPlayer player, PositionInMaze position) throws RemoteException {

        }
    }

}
