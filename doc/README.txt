# Initially we must compile the.java files
javac MembershipOperations.java
javac Utils.java
javac FileSystem.java
javac TestClient.java
javac Node.java

# In order to work with the cluster with need to create the nodes. After that, all the commands will be used by the client to work around with the nodes and the key-value pairs.
java Node 228.5.6.7 8000 127.0.0.1 8000
java Node 228.5.6.7 8000 127.0.0.2 8000
java Node 228.5.6.7 8000 127.0.0.3 8000
java Node 228.5.6.7 8000 127.0.0.4 8000

# Tests membership services and membership messages between nodes
java TestClient 127.0.0.1:8000 join
java TestClient 127.0.0.2:8000 join
java TestClient 127.0.0.3:8000 join
java TestClient 127.0.0.4:8000 join

# Tests if key:values are stored
java TestClient 127.0.0.3:8000 put Files/Test1.txt #returns code1, will be used later
java TestClient 127.0.0.1:8000 put Files/Test2.txt #returns code2, will be used later
java TestClient 127.0.0.4:8000 put Files/Test3.txt #returns code3, will be used later

# Tests key:values transfers when leaving
java TestClient 127.0.0.1:8000 leave
java TestClient 127.0.0.2:8000 leave
java TestClient 127.0.0.3:8000 leave

# Tests key:values transfers when joining
java TestClient 127.0.0.3:8000 join
java TestClient 127.0.0.2:8000 join
java TestClient 127.0.0.1:8000 join

#Tests getting same value from different nodes
java TestClient 127.0.0.1:8000 get <code1>
java TestClient 127.0.0.2:8000 get <code1>

# Tests key:value delete interface
java TestClient 127.0.0.1:8000 delete <code1>
java TestClient 127.0.0.1:8000 delete <code2>
java TestClient 127.0.0.1:8000 delete <code3>