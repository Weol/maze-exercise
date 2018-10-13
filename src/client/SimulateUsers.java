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
import java.util.concurrent.*;
import java.util.logging.Logger;

public class SimulateUsers {

    private static VirtualUser virtualUser;

    private static ScheduledThreadPoolExecutor scheduledExecutor;

    private static int interval; //How often the simulated users should move in milliseconds
    private static String host; //The address of the server host, default is local host
    private static String localhost; //The outward facing address of this program, default is local host
    private static int port; //The port of the RMI registry, default is RMIServer.getRMIPort()
    private static int amountOfUsers; //The amount of users to simulate, default is 100

    /**
     * Interprets any parameters and registers users with the GameServer
     */
    public static void main(String[] args) {
        ParameterInterpreter interpreter = new ParameterInterpreter(
                new FunctionFlag("host", "h", "The address of the server host", String::new),
                new FunctionFlag("port", "p", "The port of the host RMI registry", Integer::new),
                new FunctionFlag("users", "u", "The amount of users to simulate", Integer::new),
                new FunctionFlag("localhost", "lh", "The outside facing ip of the local machine", String::new),
                new FunctionFlag("interval", "i", "How long between user movements in milliseconds", Integer::new)
        );
        ParameterInterpretation intepretation = interpreter.intepret(args);

        host = intepretation.get("host", getLocalHostAddress()); //Set host to the host argument or local host
        localhost = intepretation.get("localhost", getLocalHostAddress()); //Set localhost to the localhost argument or local host
        port = intepretation.get("port", RMIServer.getRMIPort()); //Set port to the port argument or RMIServer.getRMIPort()
        amountOfUsers = intepretation.get("users", 100); //Set amountOfUsers to users argument or 100
        interval = intepretation.get("interval", 1000); //Set amountOfUsers to interval argument or 1000 (1 second)

        System.out.println("Setting local address to " + localhost);
        System.setProperty("java.rmi.server.hostname", localhost);

        System.out.println("Creating new scheduled thread pool executor with " + Math.max(amountOfUsers / 10, 1) + " threads");
        scheduledExecutor = new ScheduledThreadPoolExecutor(Math.max(amountOfUsers / 10, 1));

        System.out.printf("Movement interval set to %d seconds\n", interval / 1000);

        IGameServer server;
        try {
            System.out.println("Locating registry at " + host + ":" + port);
            Registry registry = LocateRegistry.getRegistry(host, port);

            System.out.println("Fetching game server");
            server = (IGameServer) registry.lookup(RMIServer.GameServerName);

            virtualUser = new VirtualUser(server.getMaze().getMaze());
        } catch (RemoteException e) {
            System.out.println("Could not connect to server, quitting");
            return;
        } catch (NotBoundException e) {
            System.out.println("Could not find any remote object with the name " + RMIServer.GameServerName + ", quitting");
            return;
        }

        System.out.println("Registering " + amountOfUsers + " users");

        int registered = 0;
        for (int i = 0; i < amountOfUsers; i++) {
            try {
                server.register(new UserImpl());
                registered++;
                if (registered % 100 == 0) {
                    System.out.println("Registered " + registered + " users");
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Registered a total of " + registered + " users");
    }

    /**
     * Called by {@link UserImpl#onGameReady} when it is ready to begin its movement. This method uses
     * {@link #scheduledExecutor} to schedule the movement of each user. Each user has a deque ({@link UserImpl#moves})
     * that contains them moves it needs to take to get through the maze or back to start. If the deque is empty then it
     * will be filled with the moves needed to go through the maze and back around.
     *
     * If a {@link RemoteException} occurs then the user will cancel its scheduled execution. If communication is
     * somehow regained after this, then {@link UserImpl#onLeaseExpired()} will return false and the user
     * will be disconnected from the server.
     *
     * If a movement is unsuccessful for any reason then the client wil cancel its scheduled execution and disconnect
     * itself.
     */
    public static void onUserReady(UserImpl user) {
        final ScheduledFuture future = scheduledExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (user.moves.size() < 1) {
                    user.moves.add(new PositionInMaze(1,0)); //The loop returns us to (0, 0) instead of (1, 0)
                    Collections.addAll(user.moves, virtualUser.getIterationLoop()); //Add a new loop to this users moves
                }

                boolean moveSuccessful = user.getPlayer().moveTo(user.moves.peek()); //Move to the next position
                if (moveSuccessful) {
                    user.moves.poll(); //If move was successful then we remove the latest movement
                } else {
                    PositionInMaze failed = user.moves.peek(); //Get the position that we failed to move to
                    System.out.println("Move unsuccessful to (" + failed.getXpos() + ", " + failed.getYpos() + ")");
                    System.out.println("Cannot recover, shutting down this user");
                    if (user.future != null) {
                        user.future.cancel(true); //Cancel this users movement execution
                    }
                    user.getGameServer().disconnect(user); //Disconnect this user
                }
            } catch (RemoteException e) {
                e.printStackTrace();
                System.out.println("Cannot recover, shutting down this user");
                if (user.future != null) {
                    user.future.cancel(true); //Cancel this users movement execution
                }
            }
        }, (long) (Math.random() * interval), interval, TimeUnit.MILLISECONDS); //We use a random initial delay to ease the load of this scheduled executor
        user.future = future;
    }

    /**
     * @return the local address or "localhost" if it cannot be found
     */
    private static String getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return "localhost";
    }

    /**
     * An implementation of {@link IUser} using the abstract class {@link User} for convenience
     */
    public static class UserImpl extends User {

        private Deque<PositionInMaze> moves; //The moves that this user must do to complete a tour of the maze

        private ScheduledFuture future; //The ScheduledFuture provided by {@link #scheduledExecutor}

        protected UserImpl() throws RemoteException {
            super();

            moves = new ArrayDeque<>();
        }

        /**
         * If this users {@link #future} is cancelled then we have no reason to continue our connection to the server.
         */
        @Override
        public boolean onLeaseExpired() throws RemoteException {
            return (!future.isCancelled());
        }

        /**

         * Called by {@link IGameServer} when this users player is ready. It fetches the players position and creates
         * a new virtual user that is used to find the path out of the maze from the players position and add it to
         * {@link #moves}.
         */
        @Override
        public void onGameReady(IGameServer gameServer, IPlayer player) throws RemoteException {
            super.onGameReady(gameServer, player);

            PositionInMaze position = player.getPosition();

            moves.addAll(Arrays.asList(new VirtualUser(getMaze(), position.getXpos(), position.getYpos()).getFirstIterationLoop()));

            onUserReady(this);
        }

        @Override
        public void onPlayerMapChange(MapChangeEvent change) throws RemoteException {
            //Don't do any processing of positions to save cpu
        }

        @Override
        public void invalidateMap() throws RemoteException {
            //Since we don't process onPlayerMapChange then there is no use to process this
        }

    }

}
