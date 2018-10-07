package client;

import mazeoblig.BoxMazeInterface;
import mazeoblig.IGameServer;
import mazeoblig.INode;
import mazeoblig.IPlayer;
import simulator.PositionInMaze;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Node extends UnicastRemoteObject implements INode {

    private static final int LOAD_AVARAGE_COUNT = 10;

    private IUser user;
    private CopyOnWriteArraySet<IUser> users;
    private ThreadPoolExecutor executor;

    public Node(IUser user) throws RemoteException {
        this.user = user;
        this.users = new CopyOnWriteArraySet<>();

        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(10);
    }

    @Override
    public void acceptUser(IUser user) {
        users.add(user);
    }

    @Override
    public void onPositionStateChange(PositionInMaze position, boolean occupied) throws RemoteException {
        user.onPositionStateChange(position, occupied);

        executor.execute(() -> {
            for (IUser user : users) {
                try {
                    user.onPositionStateChange(position, occupied);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    users.remove(user);
                }
            }
        });
    }

}
