import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/*
DATA HEADER:
HEX + LAYOUT + OBJECTS STORED + RECORD_SIZE FROM ENTITY = 16 Bytes
IDX HEADER:
HEX + LAYOT + LIVING OBJECTS STORED = 12 Bytes.
*/

public class FileDataBase {
    public final static int DAT_HEX = 0x44415441; // hexidecimal DATA
    public final static int LAYOUT = Entity.LAYOUTVER; // in case of info-block size change.
    public final static int HEXPTR = 0;
    public final static int RECPTR = 8;
    public final static int SOFDATA_HEADER = 16;

    public final static int IDX_HEX= 0x00494458; // hexidecimal IDX
    public final static int SOFIDX_HEADER = 12;

    public boolean IS_OPENED = false;

    private HashMap<Integer, Long> map;
    private RandomAccessFile dataFile;
    private RandomAccessFile hashFile;
    private String dataPath;
    private String mapPath;

    public int maxId;

    public FileDataBase(String mapPath, String dataPath) throws IOException {
        this.dataPath = dataPath;
        this.mapPath = mapPath;
        dataFile = new RandomAccessFile(this.dataPath, "rw");
        hashFile = new RandomAccessFile(this.mapPath, "rw");
        map = new HashMap<>();
        this.maxId = 0;
    }
    
    // createX - initializing method
    public void createFile() throws IOException {
        if (this.dataFile.length() != 0 || IS_OPENED == true) {
            throw new IOException();
        }
        this.dataFile.seek(0);
        this.dataFile.writeInt(DAT_HEX);
        this.dataFile.writeInt(LAYOUT);
        this.dataFile.writeInt(0); // how many objects stored;
        this.dataFile.writeInt(Entity.RECORD_SIZE);
    }

    public void createMapFile() throws IOException { // utility method for fast header creation
        this.hashFile.seek(0);
        this.hashFile.writeInt(IDX_HEX);
        this.hashFile.writeInt(LAYOUT);
        this.hashFile.writeInt(0); // how many NOT-DELETED-PAIRS.
    }


    public void loadMap() throws IOException { // recordCount - NOT DELETED PAIRS // from File to Map
        hashFile.seek(SOFIDX_HEADER - 4);
        int entryCount = hashFile.readInt();
        hashFile.seek(SOFIDX_HEADER);
        this.map.clear();
        for (int i = 0; i < entryCount; i++) {
            int id = this.hashFile.readInt();
            this.maxId = Math.max(id, this.maxId);
            long position = this.hashFile.readLong();
            if (position == -1L) {
                continue;
            }
            this.map.put(id, position);
        }
    }

    public void saveMap() throws IOException { // from Map to File
        this.hashFile.setLength(0);
        int stored = 0;
        createMapFile();
        for (Map.Entry<Integer, Long> e : map.entrySet()) { // https://docs.oracle.com/javase/8/docs/api/java/util/Map.Entry.html in case if I forget
            Long offset = e.getValue();
            this.hashFile.writeInt(e.getKey());
            this.hashFile.writeLong(e.getValue());
            stored += 1;
        }
        this.hashFile.seek(SOFIDX_HEADER - 4);
        this.hashFile.writeInt(stored);
    }

    public void rebuildHashMap() throws IOException {
        if (IS_OPENED) {
            throw new IOException("Data Base is built");
        }
        this.dataFile.seek(SOFDATA_HEADER - 4);
        int step = this.dataFile.readInt(); // RECORD_COUNT
        if (step != Entity.RECORD_SIZE) {throw new IOException("Can not rebuild Data Base: Can not work with file");}

        for(long i = SOFDATA_HEADER; i < this.dataFile.length(); i += step) {
            this.dataFile.seek(i + step-1);
            boolean isDel = this.dataFile.readBoolean();
            this.dataFile.seek(i);
            if (isDel) {
                continue;
            }
            int id = this.dataFile.readInt();
            this.map.put(id, i);
        }
        saveMap();      // includes saveMap!
    }

    public void openDataBase() throws IOException {
        if (IS_OPENED) {
            throw new IOException("CAN NOT OPEN FILE SINCE IT IS ALREADY OPENED");
        }
        if (dataFile.length() == 0) {
            createFile();
            this.hashFile.setLength(0);
        }
        dataFile.seek(0);
        hashFile.seek(0);
        int datHex = dataFile.readInt();
        int datLayout = dataFile.readInt();
        int recordCount = dataFile.readInt();
        int readSize = dataFile.readInt();
        if (this.dataFile.length() < SOFDATA_HEADER) {
            throw new IOException("The initial file is borken. Try to backup.");
        }
        if (datHex != DAT_HEX) {throw new IOException("NOT APPROPRIATE FILE");}
        if (datLayout != LAYOUT) {throw new IOException("NOT APPROPRIATE LAYOUT");}
        if ((long)recordCount * (long)Entity.RECORD_SIZE + (long)SOFDATA_HEADER != dataFile.length()) {throw new IOException("THE FILE IS INCOMPLETE, TRY TO BACKUP");}
        if (readSize != Entity.RECORD_SIZE) {throw new IOException("CAN NOT WORK WITH FILE");}

        if (hashFile.length() == 0) {
            createMapFile();
            rebuildHashMap(); // includes saveMap();
            IS_OPENED = true;
            return;
        }
        this.hashFile.seek(0);
        if (this.hashFile.length() < SOFIDX_HEADER) {
            System.out.println("[FILEDB]: The data is damaged, rebuilding from initial file.");
            rebuildHashMap();
            IS_OPENED = true;
            return;
        }
        int idxHex = hashFile.readInt();
        int idxLayout = hashFile.readInt();
        int entryCount = hashFile.readInt();
        if (idxHex != IDX_HEX) {throw new IOException("NOT APPROPRIATE UTILITY FILE");}
        if (idxLayout != LAYOUT) {throw new IOException("NOT APPROPRIATE UTILITY LAYOUT");}
        if ((long)entryCount * 12 + (long)SOFIDX_HEADER != hashFile.length()) {throw new IOException("CAN NOT WORK WITH THE UTILITY FILE");}
        loadMap();
        IS_OPENED = true;
    }

    public void closeDataBase() throws IOException {
        if (!IS_OPENED) {
            throw new IOException("Data base is already closed");
        }
        try {
            saveMap();
        } catch (IOException e) {
            System.err.println("[FILEDB]: Could not save map on closure, something happened"); // risky
            return;
        }
        this.dataFile.close();
        this.hashFile.close();
        IS_OPENED = false;
    }

    public void add(String name, int hp, double price, boolean available) throws IOException { // check if unique by id
        if (!IS_OPENED) {
            throw new IOException("[FILEDB] (EXCEPTION) On operation 'add': You have to open Data Base at first");
        }
        long offset = dataFile.getFilePointer();
        int id = ++this.maxId;
        Entity obj = new Entity(id, name, hp, price, available);
        try {
            obj.writeTo(dataFile);
        } catch (IOException e) {
            System.err.println("[FILEDB]: (EXCEPTION) On operation 'add': " + e);
            return;
        }
        this.map.put(id, offset);
        this.dataFile.seek(RECPTR);
        int recordCount = this.dataFile.readInt();
        this.dataFile.seek(RECPTR);
        this.dataFile.writeInt(recordCount + Entity.RECORD_SIZE);
    }

    public void delete() { // delete by value: it may be an id and value. In the 2nd case - delete all matching values

    }

    public void seek() { // by key/by value. If value - all matches. show at the screen the result.

    }

    public void edit() { // 

    }

    public void createBackupFile() {

    }

    public void recoverFromBackup() {

    }

    public void importFrom() {

    }
}