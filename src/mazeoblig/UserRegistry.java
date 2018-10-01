package mazeoblig;

import client.IUser;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UserRegistry extends UnicastRemoteObject implements IUserRegistry {

    private static final long LEASE_DURATION = 60; //Lease duration in seconds
    private static final int LEASE_EXPIRY_SCHEDULE_EXECUTOR_THREADPOOL_SIZE = 2;

    //The set of users currently connected, this must be a HashSet or something similar
    private final Hashtable<IUser, ILease> users;

    //A ScheduledExecutorService whose task it is to deregister clients expired leases
    private ScheduledExecutorService leaseScheduler;

    //The gameserver
    private GameServer gameServer;

    /**
     * Initialized {@link #users} and initializes {@link #leaseScheduler} with
     * {@value #LEASE_EXPIRY_SCHEDULE_EXECUTOR_THREADPOOL_SIZE} amount of threads.
     *
     * @throws RemoteException
     */
    public UserRegistry(GameServer gameServer) throws RemoteException {
        users = new Hashtable<>();
        leaseScheduler = new ScheduledThreadPoolExecutor(LEASE_EXPIRY_SCHEDULE_EXECUTOR_THREADPOOL_SIZE);
        this.gameServer = gameServer;
    }

    @Override
    public ILease register(IUser user) throws RemoteException {
        if (users.containsKey(user)) {
            throw new IllegalStateException(user.toString() + " already has a lease!");
        } else {
            ILease newLease = new Lease(user);
            users.put(user, newLease);
            gameServer.onUserConnected(user);
            return newLease;
        }
    }

    @Override
    public IUser[] getAll() throws RemoteException {
        return users.keySet().toArray(new IUser[users.size()]);
    }

    private class Lease extends UnicastRemoteObject implements ILease {

        private ScheduledFuture<?> future;
        private IUser user;

        protected Lease(IUser user) throws RemoteException {
            future = leaseScheduler.schedule(this::invalidate, LEASE_DURATION, TimeUnit.SECONDS);
            this.user = user;
        }

        private void invalidate() {
            boolean renew = false;
            try {
                renew = user.onLeaseExpired();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (!renew) {
                release();
            } else {
                renew();
            }
        }

        @Override
        public void renew() {
            synchronized (this) {
                future.cancel(true);
                future = leaseScheduler.schedule(this::invalidate, LEASE_DURATION, TimeUnit.SECONDS);
            }
        }

        @Override
        public void release() {
            synchronized (this) {
                future.cancel(true);
                users.remove(user);
                gameServer.onUserDisconnected(user);
            }
        }

    }

}
