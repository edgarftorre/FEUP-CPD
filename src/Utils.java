import java.lang.String;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;


class Pair {
    String nodeId;
    int membershipCounter;

    Pair(String nodeId, int membershipCounter) {
        this.nodeId = nodeId;
        this.membershipCounter = membershipCounter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair p)) return false;
        return this.nodeId.equals(p.nodeId);
    }
}

class Utils {

    public static List<Pair> parseOperationLog(String[] strArray) {
        List<Pair> list = new ArrayList<>();
        for (String s : strArray) {
            String[] pair = s.split("-");
            list.add(new Pair(pair[0], Integer.parseInt(pair[1])));
        }
        return list;
    }

    public static String getSha(String input) {
        // Static getInstance method is called with hashing SHA
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash =  md.digest(input.getBytes(StandardCharsets.UTF_8));

            // Conversion from hash byte array to signum representation
            BigInteger shaResult = new BigInteger(1, hash);

            // conversion from sha numbers to string hex chars
            StringBuilder hexStr = new StringBuilder(shaResult.toString(16));

            // if string doesn't have the 64 chars, fill with 0's on left
            while (hexStr.length() < 64)
                hexStr.insert(0, '0');

            return hexStr.toString();
        } catch (NoSuchAlgorithmException err){
            return "";
        }
    }

    public static int binarySearch(List<String> existingNodes, String hashedKey){ // returns the index 
        int a = 0;
        int b = existingNodes.size() - 1;
        int mid = (a + b) / 2;

        for (int i = 0; i < existingNodes.size(); i++) {
            System.out.println("existingNodes[" + i + "] = " + existingNodes.get(i));
        }

        if (Utils.getSha(existingNodes.get(a)).compareTo(hashedKey) >= 0) return a; // searched key < first key
        if (Utils.getSha(existingNodes.get(b)).compareTo(hashedKey) < 0) return a; // searched key > last key
        
        int counter = 0;
        while (a + 1 < b) {
            System.out.println("counter = " + counter);
            if (Utils.getSha(String.valueOf(existingNodes.get(mid))).compareTo(hashedKey) < 0) // searched key > midlist value key
                a = mid;
            else if (Utils.getSha(String.valueOf(existingNodes.get(mid))).compareTo(hashedKey) > 0) 
               b = mid;  // searched key <>> midlist value key
            else
                return mid; // searched key == midlist value key
               
            mid = (a + b) / 2; 
            counter++;
        }
        return b; // returns next key
    }
}