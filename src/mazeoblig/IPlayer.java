package mazeoblig;

import simulator.PositionInMaze;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IPlayer extends Remote {

    /**
     * Should return the position of the player
     *
     * @return the position of the player
     */
    PositionInMaze getPosition() throws RemoteException;

    /**
     * Should attempt to move the player and return true if the attempt was successful and return false if the attempt
     * was unsuccessful.
     *
     * @param position the position to attempt to move to
     * @return whether or not the move was successful or not
     */
    boolean moveTo(PositionInMaze position) throws RemoteException;

}
