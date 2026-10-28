import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/*
DATA HEADER:
HEX + LAYOUT + OBJECTS STORED + RECORD_SIZE FROM ENTITY + MAXID = 20 Bytes
IDX HEADER:
HEX + LAYOT + LIVING OBJECTS STORED = 16 Bytes.
*/

public class FileDataBase {
    public final static int DAT_HEX = 0x44415441; // hexidecimal DATA
    public final static int LAYOUT = Entity.LAYOUTVER; // in case of info-block size change.
    public final static int SOFDATA_HEADER = 20;
    public final static int HEXPTR = 0;
    public final static int LAYOUTPTR = 4;
    public final static int RECPTR = 8;
    public final static int RECSIZEPTR = 12;
    public final static int MAXIDPTR = 16;

    public final static int IDX_HEX= 0x00494458; // hexidecimal IDX
    public final static int SOFIDX_HEADER = 12;   // was 12
    public final static int IDX_HEXPTR    = 0;
    public final static int IDX_LAYOUTPTR = 4;
    public final static int IDX_COUNTPTR  = 8;

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
        this.dataFile.writeInt(0);
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
            // this.maxId = Math.max(id, this.maxId); // reduced
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
        this.hashFile.setLength(0);
        this.hashFile.seek(0);
        this.hashFile.writeInt(IDX_HEX);
        this.hashFile.writeInt(LAYOUT);
        this.hashFile.writeInt(map.size());
        for (Map.Entry<Integer, Long> e : map.entrySet()) { // https://docs.oracle.com/javase/8/docs/api/java/util/Map.Entry.html in case if I forget
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
        this.map.clear();
        for(long i = SOFDATA_HEADER; i < this.dataFile.length(); i += step) {
            this.dataFile.seek(i + step-1);
            boolean isDel = this.dataFile.readBoolean();
            this.dataFile.seek(i);
            if (isDel) {
                continue;
            }
            int id = this.dataFile.readInt();
            this.map.put(id, i);
            this.maxId = Math.max(id, this.maxId);
        }
        saveMap();
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

        dataFile.seek(MAXIDPTR);
        this.maxId = dataFile.readInt();
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

    private static void copyFileWithSync(File src, File dst) throws IOException {
        try (FileInputStream in  = new FileInputStream(src);
            FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.getFD().sync();
        }
    }

        public void vacuum() throws IOException {
        if (!IS_OPENED) throw new IllegalStateException("Data Base is not opened");

        File marker  = new File(dataPath + ".vacuum");
        File dataBak = new File(dataPath + ".bak");

        if (!marker.createNewFile()) {
            throw new IOException("[FILEDB] 'vacuum': Marker does not exists");
        }

        copyFileWithSync(new File(dataPath), dataBak);

        long fileLen = dataFile.length();
        int totalRecords = (int) ((fileLen - SOFDATA_HEADER) / Entity.RECORD_SIZE);

        long writePos = SOFDATA_HEADER;
        int aliveCount = 0;

        for (int i = 0; i < totalRecords; i++) {
            long readPos = SOFDATA_HEADER + (long) i * Entity.RECORD_SIZE;

            dataFile.seek(readPos + Entity.RECORD_SIZE - 1);
            if (dataFile.readBoolean()) continue;

            dataFile.seek(readPos);
            Entity obj = new Entity();
            obj.readFrom(dataFile, readPos);
            int id = obj.getId();

            if (readPos != writePos) {
                dataFile.seek(writePos);
                obj.writeTo(dataFile);
            }

            this.map.put(id, writePos);
            writePos += Entity.RECORD_SIZE;
            aliveCount++;
        }

        dataFile.setLength(writePos);
        dataFile.seek(RECPTR);
        dataFile.writeInt(aliveCount);

        saveMap();

        if (!dataBak.delete()) System.err.println("[FILEDB] 'vacuum': Could not delete backup (.bak)");
        if (!marker.delete())  System.err.println("[FILEDB] 'vacuum': Could not delete marker");

        System.out.println("[FILEDB]: vacuum OK");
    }

    public void closeDataBase() throws IOException {
        if (!IS_OPENED) {
            throw new IOException("Data base is already closed");
        }
        try {
            saveMap();
        } catch (IOException e) {
            System.err.println("[FILEDB]: Could not save map on closure, something happened: " + e); // risky
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
        long offset = dataFile.length();
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
        this.dataFile.writeInt(recordCount + 1);

        this.dataFile.seek(MAXIDPTR);
        this.dataFile.writeInt(this.maxId);
    }

    public void deleteByID(int id) { // delete by value: it may be an id and value. In the 2nd case - delete all matching values
        /*
        Seek for id in HashMap at first and make it 
        */
    }

    public void deleteByVal() {

    }

    public void seekByID() { // by key/by value. If value - all matches. show at the screen the result.

    }

    public void seekByValue() {

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