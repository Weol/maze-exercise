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

    private static List<UserImpl> readyUsers = new ArrayList<>();

    public static void main(String[] args) throws RemoteException, NotBoundException, InterruptedException {
        System.setProperty("java.rmi.server.hostname", args[1]);

        Registry registry = LocateRegistry.getRegistry(args[0], RMIServer.getRMIPort());
        IUserRegistry userRegistry = (IUserRegistry) registry.lookup(RMIServer.UserRegistryName);

        int count = 2000;

        for (int i = 0; i < count; i++) {
            userRegistry.register(new UserImpl());
        }

        int nThreads = 10;
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(nThreads);

        int usersPerThread = count / nThreads;

        for (int i = 0; i < nThreads; i++) {
            List<UserImpl> subUsers = readyUsers.subList(i * usersPerThread, Math.min((i+1)*usersPerThread, readyUsers.size()));
            System.out.printf("Thread %d handles users %d to %d\n", i, i * usersPerThread, Math.min((i+1)*usersPerThread, readyUsers.size()));
            executor.scheduleWithFixedDelay(() -> {
                for (UserImpl subUser : subUsers) {
                    if (subUser.moves.size() != 0) {
                        try {
                            subUser.getPlayer().moveTo(subUser.moves.poll());
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

    public static class UserImpl extends User {

        private Deque<PositionInMaze> moves;
        private List<PositionInMaze> round;

        protected UserImpl() throws RemoteException {
            moves = new LinkedList<>();
            round = new ArrayList<>();
        }

        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            super.onGameReady(gameServer, player);

            VirtualUser virtualUser = new VirtualUser(getMaze());

            moves.addAll(Arrays.asList(virtualUser.getFirstIterationLoop()));
            round.addAll(Arrays.asList(virtualUser.getIterationLoop()));

            moves.addAll(round);

            readyUsers.add(this);
        }

        @Override
        public void onPositionStateChange(PositionInMaze position, boolean occupied) throws RemoteException {

        }

        @Override
        public INode requestNode() throws RemoteException {
            return new Node(this);
        }
    }

}
