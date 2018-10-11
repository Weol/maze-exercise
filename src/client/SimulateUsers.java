package client;

import javafx.geometry.Pos;
import mazeoblig.*;
import paramaters.FunctionFlag;
import paramaters.ListFlag;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimulateUsers {

    private static VirtualUser virtualUser;

    private static ScheduledThreadPoolExecutor scheduledExecutor;
    private static Executor executor;

    private static UserImpl[] simulatedUsers;

    private static int interval;

    private static final Logger LOGGER = Logger.getLogger(SimulateUsers.class.getName());

    public static void main(String[] args) throws RemoteException, NotBoundException, InterruptedException {
        ParameterInterpreter interpreter = new ParameterInterpreter(
                new FunctionFlag("host", "h", "The address of the server host", String::new),
                new FunctionFlag("port", "p", "The port of the host RMI registry", Integer::new),
                new FunctionFlag("users", "u", "The amount of users to simulate", Integer::new),
                new FunctionFlag("threads", "t", "The amount of threads to use", Integer::new),
                new FunctionFlag("localhost", "lh", "The outside facing ip of the local machine", String::new),
                new FunctionFlag("interval", "i", "How long between user movements in milliseconds", Integer::new)
        );
        ParameterInterpretation intepretation = interpreter.intepret(args);

        String host = intepretation.get("host", getLocalHostAddress());
        String localhost = intepretation.get("localhost", getLocalHostAddress());
        int port = intepretation.get("port", RMIServer.getRMIPort());
        int amountOfUsers = intepretation.get("users", 100);
        int threads = intepretation.get("threads", Math.max(amountOfUsers / 10, 1));
        interval = intepretation.get("interval", 1000);

        System.out.println("Setting local address to " + localhost);
        System.setProperty("java.rmi.server.hostname", localhost);

        System.out.println("Locating registry at " + host + ":" + port);
        Registry registry = LocateRegistry.getRegistry(host, port);

        System.out.println("Fetching game server");
        IGameServer server = (IGameServer) registry.lookup(RMIServer.GameServerName);

        System.out.println("Creating new thread pool executor executor with " + threads + " threads");
        executor = Executors.newFixedThreadPool(threads);

        scheduledExecutor = new ScheduledThreadPoolExecutor(1);

        System.out.printf("Movement interval set to %d seconds\n", interval / 1000);

        System.out.println("Registering " + amountOfUsers + " users");

        simulatedUsers = new UserImpl[amountOfUsers];
        for (int i = 0; i < amountOfUsers; i++) {
            try {
                server.register(new UserImpl(i));
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        scheduledExecutor.scheduleWithFixedDelay(() -> {
            for (UserImpl simulatedUser : simulatedUsers) {
                executor.execute(() -> {
                    try {
                        if (simulatedUser.moves.size() < 1) {
                            Collections.addAll(simulatedUser.moves, virtualUser.getIterationLoop());
                        }

                        boolean moveSucessful = simulatedUser.getPlayer().moveTo(simulatedUser.moves.peek());
                        if (moveSucessful) {
                            simulatedUser.moves.poll();
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                });
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    public static void onUserReady(UserImpl user) {
        simulatedUsers[user.index] = user;
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
        private int index;

        protected UserImpl(int i) throws RemoteException {
            super();
            index = i;
            moves = new ArrayDeque<>();
        }

        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            super.onGameReady(gameServer, player);

            PositionInMaze position = player.getPosition();

            if (virtualUser == null) {
                virtualUser = new VirtualUser(getMaze());
            }

            moves.addAll(Arrays.asList(new VirtualUser(getMaze(), position.getXpos(), position.getYpos()).getFirstIterationLoop()));

            onUserReady(this);
        }

        @Override
        public void onPositionStateChange(List<PositionChange> change) throws RemoteException {

        }

    }

}
