package mazeoblig;

import client.IUser;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IUserRegistry extends Remote {

    ILease register(IUser user) throws RemoteException;

    IUser[] getAll() throws RemoteException;

}
