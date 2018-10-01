package mazeoblig;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ILease extends Remote {

    void renew() throws RemoteException;

    void release() throws RemoteException;

}
