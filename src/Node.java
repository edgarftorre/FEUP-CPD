import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.UnknownHostException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Node implements MembershipOperations {

    public String ipAddr;
    public int ipPort;
    public String nodeId;
    public int storePort;

    public int membershipCounter;
    public String hashedId;

    public InetAddress group;
    public MulticastSocket socketUDP;
    public ServerSocket socketTCP;

    public List<String> existingNodes;
    public List<Pair> operationLog;

    public static void main(String[] args) {
        //java Node IP_BROADCAST 8000 127.0.0.1 8000
        if (args.length < 4) {
            System.out.println("Incorrect number of arguments");
        }
        String ipAddr = args[0];
        int ipPort = Integer.parseInt(args[1]);
        String nodeId = args[2];
        int storePort = Integer.parseInt(args[3]);

        Node node = new Node(ipAddr, ipPort, nodeId, storePort);

        String nodeIdReverse = new StringBuilder(nodeId).reverse().toString();
        try {
            MembershipOperations stub = (MembershipOperations) UnicastRemoteObject.exportObject(node, 0);
            Registry registry = LocateRegistry.createRegistry(Integer.parseInt(nodeIdReverse.substring(0, nodeIdReverse.indexOf("."))));
            registry.rebind("Node", stub);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Node(String ipAddr, int ipPort, String nodeId, int storePort) {
        this.ipAddr = ipAddr;
        this.ipPort = ipPort;
        this.nodeId = nodeId;
        this.storePort = storePort;
        this.membershipCounter = -1;

        this.hashedId = Utils.getSha(nodeId);

        this.existingNodes = new ArrayList<>();
        this.operationLog = new ArrayList<>();

        FileSystem.createFolder(hashedId);
        FileSystem.createFile("StoreSystem/node" + hashedId + "/membership");
        FileSystem.writeFile("StoreSystem/node" + hashedId + "/membership", String.valueOf(membershipCounter));
        // Continuar aqui
    }

    public void openUDPport() {
        try {
            this.group = InetAddress.getByName(ipAddr);
            this.socketUDP = new MulticastSocket(ipPort);
            socketUDP.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false);

            new Thread(() -> {
                try {
                    byte[] buf = new byte[socketUDP.getReceiveBufferSize()];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    System.out.println("UDP server open at port " + ipPort);
                    do {
                        socketUDP.receive(packet);

                        // Removes extra NULL chars
                        String parsedData = new String(packet.getData(), StandardCharsets.UTF_8);
                        parsedData = parsedData.substring(0, parsedData.indexOf((char) 0) == -1 ? parsedData.length() : parsedData.indexOf((char) 0));
                        packet.setData(parsedData.getBytes());
                        System.out.println("Received data from UDP -> " + parsedData);

                        processUDPpacket(packet);
                    } while (true);
                } catch (IOException ignored) {
                } finally {
                    System.out.println("UDP server closed");
                }
            }).start();
        } catch (SocketException e) {
            System.out.println("Closing UDP server");
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    public void processUDPpacket(DatagramPacket packet) {
        String data = new String(packet.getData(), StandardCharsets.UTF_8);
        String[] dataSplit = data.split(",");
        String senderNodeId = dataSplit[0];
        int ipPort = Integer.parseInt(dataSplit[1]);

        if (dataSplit[2].substring(0, Math.min(dataSplit[2].length(), 10)).equals("membership")) { // Periodic UDP membership messages from most updated node
            if (!(this.nodeId.equals(senderNodeId) && this.ipPort == ipPort)) // Don't process loopbacks
                processUDPMembershipMessage(data);
            return;
        }

        int membershipCounter = Integer.parseInt(dataSplit[2]);
        addToLog(senderNodeId, membershipCounter);

        if (membershipCounter % 2 == 0) { // Join
            System.out.println("Received join!");
            if (existingNodes.isEmpty())
                existingNodes.add(senderNodeId);
            else {
                int index = 0;
                if (existingNodes.size() == 1) {
                    if (Utils.getSha(existingNodes.get(0)).compareTo(Utils.getSha(senderNodeId)) < 0)
                        index = 1;
                }
                else
                    index = Utils.binarySearch(existingNodes, Utils.getSha(senderNodeId));
                        
                existingNodes.add(index, senderNodeId);
                transferKeysToNew(senderNodeId);
                FileSystem.createFolder(Utils.getSha(senderNodeId));
            }

            if (true) { //TODO: restringir quais/quantos nodes enviam membership message ao novo node
                sendTCPmembershipMessage(senderNodeId, ipPort);
            }
        }
        else { // Leave
            System.out.println("Received leave!");
            existingNodes.remove(senderNodeId);
        }

    }

    public void processUDPMembershipMessage(String message) {
        message = message.split("membership:")[1];
        String[] messageSplit = message.split(":");
        String[] hashValuesSplit = messageSplit[1].split(",");
        String[] pairsSplit = messageSplit[2].split(",");

        this.existingNodes = new ArrayList<>();
        existingNodes.addAll(Arrays.asList(hashValuesSplit));


        this.operationLog = Utils.parseOperationLog(pairsSplit);
        this.operationLog = new ArrayList<>();
        for (String s : pairsSplit) {
            String[] pair = s.split("-");
            operationLog.add(new Pair(pair[0], Integer.parseInt(pair[1])));
        }
    }


    public void openTCPport() {
        try {
            this.socketTCP = new ServerSocket();
            socketTCP.bind(new InetSocketAddress(nodeId, storePort));

            new Thread(() -> {
                ExecutorService executor = null;
                try {
                    executor = Executors.newFixedThreadPool(5); // TODO: set number of threads
                    System.out.println("TCP server open at port " + storePort);
                    while (true) {
                        final Socket socket = socketTCP.accept();
                        executor.execute(() -> {
                            try {
                                String input = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
                                System.out.println("Received by TCP: " + input); // Debug

                                String reply;
                                if ((reply = processTCPmessage(input)) != null) {
                                    System.out.println("Sending this to client: " + reply);
                                    new PrintWriter(socket.getOutputStream(), true).println(reply);
                                }

                            } catch (IOException ioe) {
                                ioe.printStackTrace();
                            } finally {
                                try {
                                    System.out.println("Closing TCP socket");
                                    socket.close();
                                } catch (IOException ioe) {
                                    ioe.printStackTrace();
                                }
                            }
                        });
                    }
                } catch (IOException ignored) {
                } finally {
                    System.out.println("TCP server closed");
                    if (executor != null) {
                        executor.shutdown();
                    }
                }
            }).start();
        } catch (IOException e) {
            System.out.println("Error opening TCP server");
            e.printStackTrace();
        }
    }

    public String processTCPmessage(String message) {
        // Get, put, delete, TCP membership after joining
        
        String[] msgSplit = message.split(",");

        if (msgSplit.length > 2) {
            if (msgSplit[2].length() > 10) { // TCP Membership message
                String[] messageSplit = message.split(":");
                String[] hashValuesSplit = messageSplit[1].split(",");
                String[] pairsSplit = messageSplit[2].split(",");
        
                this.existingNodes = new ArrayList<>();
                Collections.addAll(existingNodes, hashValuesSplit);
        
                this.operationLog = Utils.parseOperationLog(pairsSplit);
        
                //Debug
                for (String nodeId : existingNodes)
                    System.out.println("NodeId = " + nodeId);
                for (Pair op : operationLog)
                    System.out.println("NodeId:MembCounter = " + op.nodeId + ":" + op.membershipCounter);

                return null;
            }
        }

        String value = msgSplit[3];
        String keyHash = Utils.getSha(value);

        System.out.println("Keyhash = " + keyHash);
        System.out.println("My hashedId = " + hashedId);
        for (int i = 0; i < existingNodes.size(); i++) {
            System.out.println("node[" + i + "].hashedId = " + Utils.getSha(existingNodes.get(i)));
        }

        int correctNodeIndex = Utils.binarySearch(existingNodes, keyHash);
        if (existingNodes.indexOf(nodeId) != correctNodeIndex) {
            System.out.println("Redirected message: '" + message+ "'");
            return sendTCPmessage(existingNodes.get(correctNodeIndex), storePort, message);
        }

        switch (msgSplit[2]) {
            case "put":
                for (int i = 3; i < msgSplit.length; i++) {
                    String messageHash = Utils.getSha(msgSplit[i]);
                    File file = FileSystem.createFile("StoreSystem\\node" + hashedId + "\\" + messageHash);
                    if (file == null) break;
                    FileSystem.writeFile(file.getAbsolutePath(), msgSplit[i]);
                }
                return null;

            case "get":
                System.out.println("Received get value whose key = " + msgSplit[3]);
                return FileSystem.readFile("StoreSystem\\node" + hashedId + "\\" + msgSplit[3]);
            case "delete":
                if (FileSystem.deleteFile("StoreSystem\\node" + hashedId + "\\" + msgSplit[3]) == 0) return message;
                return null;
            default:
                return null;
        }
    }

    public String sendTCPmessage(String nodeIpAddr, int nodePort, String message) {
        System.out.println("Sending by TCP: " + message);
        String res = null;
        try {
            Socket socket = new Socket(InetAddress.getByName(nodeIpAddr), nodePort); // TODO: check if correct
            new PrintWriter(socket.getOutputStream(), true).println(message); //TODO: check if correct
            res = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
            socket.close();
        } catch (UnknownHostException e) {
            System.out.println("Unknown Host Exception at sendTCPMessage");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("IO Exception at sendTCPMessage");
            e.printStackTrace();
        }

        return res;
    }

    public void sendTCPmembershipMessage(String nodeIpAddr, int nodePort) {
        StringBuilder message = new StringBuilder();
        message.append(this.nodeId).append(",").append(this.ipPort).append(",membership:");

        // append current existing nodes
        message.append(existingNodes.get(0));
        for (int i = 1; i < existingNodes.size(); i++)
            message.append(",").append(existingNodes.get(i));
        
        message.append(":");

        // append operation log pairs
        message.append(operationLog.get(0).nodeId).append("-").append(operationLog.get(0).membershipCounter);
        for (int i = 1; i < operationLog.size(); i++)
            message.append(",").append(operationLog.get(i).nodeId).append("-").append(operationLog.get(i).membershipCounter);

        sendTCPmessage(nodeIpAddr, nodePort, message.toString());
    }

    @Override
    public void join() throws RemoteException {
        System.out.println("Join was called"); // Debug purposes
        this.membershipCounter = Integer.parseInt(FileSystem.readFile("StoreSystem/node" + hashedId + "/membership"));

        if (membershipCounter % 2 == 0) {
            System.out.println("This node already joined its cluster.");
            return;
        }

        openUDPport();
        openTCPport();

        membershipCounter++;
        
        addToLog(nodeId, membershipCounter);
        
        if (existingNodes.isEmpty())
            existingNodes.add(nodeId);
        else {
            int index = Utils.binarySearch(existingNodes, hashedId);
            existingNodes.add(index, nodeId);
        }
        
        FileSystem.writeFile("StoreSystem/node" + hashedId + "/membership", String.valueOf(membershipCounter));

        String data = nodeId + "," + storePort + "," + membershipCounter + (char)0;
        try {
            DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(), group, ipPort);
            System.out.println("join() will send this msg by UDP: " + new String (packet.getData(), StandardCharsets.UTF_8));
            socketUDP.send(packet);
            socketUDP.joinGroup(group);
            //socketUDP.joinGroup(new InetSocketAddress(ipAddr, ipPort), new NetworkInterface());
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Exiting join function");
    }

    @Override
    public void leave() throws RemoteException {
        System.out.println("Leave was called");  // Debug purposes
        if (membershipCounter % 2 != 0) {
            System.out.println("This node already left its cluster");
            return;
        }

        membershipCounter++;

        addToLog(nodeId, membershipCounter);
        FileSystem.writeFile("StoreSystem/node" + hashedId + "/membership", String.valueOf(membershipCounter));

        String data = nodeId + "," + storePort + "," + membershipCounter + (char)0;
        try {
            DatagramPacket packet = new DatagramPacket(data.getBytes(), data.length(), group, ipPort);
            System.out.println("leave() will send this msg by UDP: "+ new String (packet.getData(), StandardCharsets.UTF_8));
            socketUDP.leaveGroup(group);
            socketUDP.send(packet);

            // Transfer keys:values to next node
            int nextIndex = (Utils.binarySearch(existingNodes, hashedId) + 1) % existingNodes.size();
            transferKeysToNext(existingNodes.get(nextIndex));

            existingNodes.remove(nodeId);
            socketUDP.close();
            socketTCP.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void transferKeysToNext(String ipAddr){
        File dir = new File("StoreSystem/node" + hashedId);
            File[] dirList = dir.listFiles();
            if (dirList != null) {
                
                boolean willSendSomething = false;
                StringBuilder sb = new StringBuilder(nodeId).append(",").append(storePort).append(",").append("put");

                for (File file : dirList) {    
                    if (!file.getName().equals("membership")) {
                        willSendSomething = true;
                        String value = FileSystem.readFile("StoreSystem/node" + hashedId + "/" + file.getName());
                        if (value == null) return;
                        sb.append(",").append(value);
                        FileSystem.deleteFile("StoreSystem/node" + hashedId + "/" + file.getName());
                    }
                }

                if (willSendSomething)
                    sendTCPmessage(ipAddr, storePort, sb.toString()); // the 2nd arg should be the storePort of the node whose ID is this func's argument.. no idea how
            }
    }

    public void transferKeysToNew(String newNodeId){
        String newKeyHash = Utils.getSha(newNodeId);
        System.out.println("TransferKeysToNew called");

        int index = (Utils.binarySearch(existingNodes, newKeyHash) + 1) % existingNodes.size(); // next to new id

        if (existingNodes.get(index).equals(nodeId)) {
            File dir = new File("StoreSystem/node" + hashedId);
            File[] dirList = dir.listFiles();
            if (dirList != null) {
                
                boolean willSendSomething = false;
                StringBuilder sb = new StringBuilder(nodeId).append(",").append(storePort).append(",").append("put");
                
                for (File file : dirList) {
                    if (!file.getName().equals("membership") && file.getName().compareTo(newKeyHash) <= 0) {

                        willSendSomething = true;
                        String value = FileSystem.readFile("StoreSystem/node" + hashedId + "/" + file.getName());
                        if (value == null) return;
                        sb.append(",").append(value);                       
                        FileSystem.deleteFile("StoreSystem/node" + hashedId +"/" + file.getName());
                    }
                }

                if (willSendSomething)
                    sendTCPmessage(newNodeId, storePort, sb.toString()); // the 2nd arg should be the storePort of the node whose ID is this func's argument.. no idea how
            }
        }
    }

    public void addToLog(String nodeId, int membershipCounter) {
        Pair pair = new Pair (nodeId, membershipCounter);

        if (operationLog == null)
            operationLog = new ArrayList<>();

        operationLog.remove(pair);
        operationLog.add(0, pair);
    }
}