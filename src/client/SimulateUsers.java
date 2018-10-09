package client;

import mazeoblig.*;
import paramaters.FunctionFlag;
import paramaters.ParameterInterpretation;
import paramaters.ParameterInterpreter;
import simulator.PositionInMaze;
import simulator.VirtualUser;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SimulateUsers {

    private static VirtualUser virtualUser;

    private static ScheduledThreadPoolExecutor scheduledExecutor;

    private static int interval;

    public static void main(String[] args) throws RemoteException, NotBoundException, InterruptedException {
        ParameterInterpreter interpreter = new ParameterInterpreter(
                new FunctionFlag("host", "h", "The address of the server host", String::new),
                new FunctionFlag("port", "p", "The port of the host RMI registry", Integer::new),
                new FunctionFlag("users", "u", "The amount of users to simulate", Integer::new),
                new FunctionFlag("localhost", "lh", "The outside facing ip of the local machine", String::new),
                new FunctionFlag("interval", "i", "How long between user movements in milliseconds", Integer::new)
        );
        ParameterInterpretation intepretation = interpreter.intepret(args);

        String host = intepretation.get("host", getLocalHostAddress());
        String localhost = intepretation.get("localhost", getLocalHostAddress());
        int port = intepretation.get("port", RMIServer.getRMIPort());
        int amountOfUsers = intepretation.get("users", 100);
        interval = intepretation.get("interval", 1000);

        System.out.println("Setting local address to " + localhost);
        System.setProperty("java.rmi.server.hostname", localhost);

        System.out.println("Locating registry at " + host + ":" + port);
        Registry registry = LocateRegistry.getRegistry(host, port);

        System.out.println("Fetching game server");
        IGameServer server = (IGameServer) registry.lookup(RMIServer.GameServerName);

        System.out.println("Creating new scheduled executor with " + Math.min(amountOfUsers / 100, 4) + " threads");
        scheduledExecutor = new ScheduledThreadPoolExecutor(Math.min(amountOfUsers / 100, 4));

        System.out.println("Registering " + amountOfUsers + " users");
        for (int i = 0; i < amountOfUsers; i++) {
            try {
                server.register(new UserImpl());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public static void onUserReady(UserImpl user) {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            System.out.println(scheduledExecutor.getQueue().size());
            if (user.moves.size() < 1) {
                user.moves.addAll(Arrays.asList(virtualUser.getIterationLoop()));
            }
            try {
                user.getPlayer().moveTo(user.moves.poll());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    private static String getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return "localhost";
    }

    public static class UserImpl extends User {

        private Deque<PositionInMaze> moves;

        protected UserImpl() throws RemoteException {
            super();
            moves = new ArrayDeque<>();
        }

        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            super.onGameReady(gameServer, player);

            if (virtualUser == null) {
                System.out.println("Setting");
                virtualUser = new VirtualUser(getMaze());
            }

            moves.addAll(Arrays.asList(new VirtualUser(getMaze()).getFirstIterationLoop()));

            onUserReady(this);
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
