package mazeoblig;

import client.IUser;

public interface INodeManager {

    void migrateUsers(IUser[] users);

    void notifyNodeUnavailable(INode node);

    void notifyNodeAvailable(INode node);

}
