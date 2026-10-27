import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Set;

public class FileDataBase {
    public final static int DAT_HEX = 0x44415441; // hexidecimal DATA
    public final static int LAYOT = 1; // in case of info-block size change. 
    public final static int SOFDATA_HEADER = 16;

    public final static int IDX_HEX= 0x00494458; // hexidecimal IDX
    public final static int SOFIDX_HEADER = 12;

    public boolean IS_OPENED = false;

    private HashMap<Integer, Long> map;
    private RandomAccessFile dataFile;
    private RandomAccessFile hashFile;
    private String dataPath;
    private String mapPath;

    public FileDataBase(String mapPath, String dataPath) throws IOException {
        this.dataPath = dataPath;
        this.mapPath = mapPath;
        dataFile = new RandomAccessFile(this.dataPath, "rw");
        hashFile = new RandomAccessFile(this.mapPath, "rw");
        map = new HashMap<>();
    }
    
    // createX - initializing method
    public void createFile() throws IOException {
        if (this.dataFile.length() != 0 || IS_OPENED == true) {
            //throw smth
        }
        this.dataFile.seek(0);
        this.dataFile.writeInt(DAT_HEX);
        this.dataFile.writeInt(LAYOT);
        this.dataFile.writeInt(0); // how many objects stored;
        this.dataFile.writeInt(Entity.RECORD_SIZE);
    }

    public void createMap() throws IOException { // utility methid for fast header creation
        this.hashFile.seek(0);
        this.hashFile.writeInt(IDX_HEX);
        this.hashFile.writeInt(LAYOT);
        this.hashFile.writeInt(0); // how many NOT-DELETED-PAIRS.
    }


    public void loadMap() throws IOException { // recordCount - NOT DELETED PAIRS
        if (IS_OPENED) {
            // throw smth
        }
        hashFile.seek(SOFIDX_HEADER - 4);
        int entryCount = hashFile.readInt();
        hashFile.seek(SOFIDX_HEADER);
        this.map.clear();
        for (int i = 0; i < entryCount; i++) {
            int id = this.hashFile.readInt();
            long position = this.hashFile.readLong();
            if (position == -1) {
                continue;
            }
            this.map.put(id, position);
        }
    }

    public void saveMap() throws IOException {
        if (!IS_OPENED) {
            // throw smth
            // cause you can't save map if the hashMap file stream closed.
        }
        this.hashFile.setLength(0);
        int stored = 0;
        createMap();
        Set<Integer> keys = this.map.keySet();
        for (int id : keys) {
            if (map.get(id) == -1) {
                continue;
            }
            this.hashFile.writeInt(id);
            this.hashFile.writeLong(this.map.get(id));
            stored += 1;
        }
        this.hashFile.seek(SOFIDX_HEADER - 4);
        this.hashFile.writeInt(stored);
        this.hashFile.close();
    }

    

}