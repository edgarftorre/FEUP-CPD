import java.io.*;
import java.net.*;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class TestClient {

    public static void main(String[] args) {
        //java TestClient <node_ap> <operation> [<opnd>]
        if (args.length < 2) {
            System.out.println("Too few arguments for any operation");
            return;   
        }

        String operation = args[1];

        String nodeIdReverse = new StringBuilder(args[0].split(":")[0]).reverse().toString();
        int port = Integer.parseInt(nodeIdReverse.substring(0, nodeIdReverse.indexOf(".")));

        if (operation.equals("join") || operation.equals("leave")) {
            try {
                Registry registry = LocateRegistry.getRegistry(port);
                MembershipOperations stub = (MembershipOperations) registry.lookup("Node");
                if (operation.equals("join")) stub.join();
                else stub.leave();
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }

            System.out.println("Function " + args[1] + " was called successfully!");
            return;
        }

        if (args.length < 3) {
            System.out.println("Too few arguments for key-value operations");
            return;
        }

        String nodeIPaddress = args[0].split(":")[0];
        int nodeIPport = Integer.parseInt(args[0].split(":")[1]);
        String operand = args[2];

        try {
            Socket socket = new Socket(InetAddress.getByName(nodeIPaddress), nodeIPport);
            
            if (operation.equals("put")) {
                operand = FileSystem.readFile(operand);
                System.out.println("Your key for value \"" + operand + "\" is: " + Utils.getSha(operand));
            }
            
            String message = nodeIPaddress + "," + nodeIPport + "," + operation + "," + operand;

            //Send message to Node
            new PrintWriter(socket.getOutputStream(), true).println(message);

            if (operation.equals("get")) {
                System.out.println("Your value is " + new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Function " + args[1] + " was called successfully!");

    }
}
