import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;

public class FileDataBase {
    public final static int DAT_MAGIC = 0x44415441; // hexidecimal DATA
    public final static int IDX_MAGIC = 0x494458; // hexidecimal IDX
    public static boolean IS_OPENED = false;
    public final static int HEADER_SIZE = 4;

    private HashMap<Integer, Long> map;
    private RandomAccessFile dataFile;
    private String dataPath;
    private String mapPath;

    public FileDataBase(String mapPath, String dataPath) throws FileNotFoundException {
        this.dataPath = dataPath;
        this.mapPath = mapPath;
        dataFile = null;
    }

    public void openDB() throws IOException {
        if (IS_OPENED == true) {
            // throw smth
        }
        this.dataFile = new RandomAccessFile(this.dataPath, "rw");
        if (dataFile.length() == 0) {
            dataFile.writeInt(DAT_MAGIC);
        }
        IS_OPENED = true;
        System.out.println("[FILEDB]: The data base file has been opened");
    }

    public void closeDB() throws IOException {
        this.dataFile.close();
        
        IS_OPENED = false;
    }
}