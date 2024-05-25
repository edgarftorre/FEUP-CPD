import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MembershipOperations extends Remote {
    void join() throws RemoteException;
    void leave() throws RemoteException;
}
