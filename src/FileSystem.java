import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class FileSystem{

    public static File createFolder(String nodeId){
        try {
            File file = new File("StoreSystem/node" + nodeId);
            file.mkdir();
            return file;

        } catch (SecurityException err) {
            System.out.println("Error creating folder");
            err.printStackTrace();
            return null;
        }
    }

    public static File createFile(String pathname){
        try {
            File file = new File(pathname);
            file.createNewFile();
            return file;

        } catch (IOException err) {
            System.out.println("Error creating file");
            err.printStackTrace();
            return null;
        }
    }

    public static String readFile(String pathname){
        StringBuilder str = new StringBuilder();
        File file = new File(pathname);
        try{
            Scanner reader = new Scanner(file);
            while (reader.hasNextLine())
                str.append(reader.nextLine());

            reader.close();
            return str.toString();

        } catch (FileNotFoundException err) {
            System.out.println("Error reading file");
            err.printStackTrace();
            return null;
        }
    }

    public static void writeFile(String pathname, String text){

        try {
            FileWriter myWriter = new FileWriter(pathname);
            myWriter.write(text);
            myWriter.close();

        } catch (IOException e) {
            System.out.println("Error writing file");
            e.printStackTrace();
        }
    }

    public static Integer deleteFile(String pathname){
        File file = new File(pathname);
        boolean bool = file.delete();
        if (bool) return 0;
        return 1;
    }

    public static void main(String[] args) {
       //System.out.println("Hello world!");
       //File folder = createFolder(1);
       //File file = createFile(1);
       //writeFile(1, "file1", "boas");
       //System.out.println(readFile(1, "file1"));
    }
}