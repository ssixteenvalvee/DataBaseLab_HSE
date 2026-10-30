import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    public enum Field {
        ID, NAME, HP, PRICE, AVAILABLE
    }

    private final HashMap<Integer, Long> map;
    private RandomAccessFile dataFile;
    private RandomAccessFile hashFile;
    private final String dataPath;
    private final String mapPath;
    private final String backupPath;

    public int maxId;

    public FileDataBase(String mapPath, String dataPath) throws IOException {
        this.dataPath = dataPath;
        this.mapPath = mapPath;
        dataFile = new RandomAccessFile(this.dataPath, "rw");
        hashFile = new RandomAccessFile(this.mapPath, "rw");
        map = new HashMap<>();
        this.maxId = 0;
        backupPath = "/backups";
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
        this.dataFile.seek(RECSIZEPTR);
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

        File marker = new File(this.dataPath + ".vacuum");
        File bak = new File(this.dataPath + ".bak");
        if (marker.exists()) {
            System.out.println("[FILEDB]: interfered vacuum op found. Recovering...");
            this.dataFile.close();
            this.hashFile.close();
            copyFileWithSync(bak, new File(dataPath));
            new File(mapPath).delete();
            this.dataFile = new RandomAccessFile(dataPath, "rw");
            this.hashFile = new RandomAccessFile(mapPath, "rw");
            marker.delete();
            bak.delete();
        }

        if (dataFile.length() == 0) {
            createFile();
            this.hashFile.setLength(0);
        }this.dataFile = new RandomAccessFile(this.dataPath, "rw");
        this.hashFile = new RandomAccessFile(this.mapPath, "rw");
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
        if (!IS_OPENED) throw new IllegalArgumentException("Data Base is not opened");

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
        } finally {
            this.dataFile.close();
        this.hashFile.close();
        IS_OPENED = false;
        }
    }

    public void add(String name, int hp, double price, boolean available) throws IOException { // check if unique by id
        if (!IS_OPENED) {
            throw new IOException("[FILEDB] (EXCEPTION) On operation 'add': You have to open Data Base at first");
        }
        long offset = dataFile.length();
        int id = ++this.maxId;
        dataFile.seek(offset);
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

        this.dataFile.seek(this.dataFile.length()); // !!!
    }

    public void deleteByID(int id) throws IOException { // delete by value: it may be an id and value. In the 2nd case - delete all matching values
        /*
        Seek for id in HashMap at first and make it non-living
        map.remove!!
        */
       if (!IS_OPENED) {
        throw new IllegalArgumentException("Data base is not opened");
       }
       if (!this.map.containsKey(id)) {
            throw new IllegalArgumentException("[FILEDB] deletebyID: No such id");
       } else {
        long offset = this.map.get(id); // gives offset/position
        this.dataFile.seek(offset + Entity.SEEK_DEL); // finds del flag
        this.dataFile.writeBoolean(true); // makes it deleted (logically)

        this.map.remove(id);
       }
    }

    public ArrayList<Entity> deleteByVal(Field f, Object value) throws IOException {
        /*
            min O(n) potomuchto a kak eshe epta?
            -> take the val (bool/int/double/string)

            if opened - good
            seek by value all matching things
            get their ids
            delete all in order
        */
        if (!IS_OPENED) {
            throw new IllegalArgumentException("Data base is not opened");
        }
        ArrayList<Entity> deleted = seekByValue(f, value);
        for (Entity obj : deleted) {
            deleteByID(obj.getId());
        }
        return deleted;
    }

    public Entity seekByID(int id) throws IOException {
        /*
        if opened -> good
        if given key in map -> good
        take offset, which attached to the key
        seek by this offset a byte block
        read it into entity
        give it to the whom who asked
        */
        if (!IS_OPENED) {
            throw new IllegalArgumentException("Data base is not opened");
        }
        long offset;
        if (this.map.containsKey(id)) {
            offset = this.map.get(id);
            this.dataFile.seek(offset);
            Entity obj = new Entity();
            obj.readFrom(this.dataFile, offset);
            this.dataFile.seek(this.dataFile.length());
            return obj;
        } else {
            throw new IllegalArgumentException("[FILEDB] seekById (EXCEPTION): No such id " + id);
        }
    }

    public ArrayList<Entity> seekByValue(Field f, Object value) throws IOException {

        /*
        IF OPEN - good
        then get keys
        then go through this keys and take a position in file
        then go to the position and tell entity to read RECORD_SIZE bytes of information
        then, in CASE if front asked for id, CASE ID
            if it matches (equals() or ==) -> good
            store it to the "all matches box"
                else just skip it
        then give the box with all matches to the one who asked (frontend)
        */

        if (!IS_OPENED) {
            throw new IllegalArgumentException("Data base is not opened");
        }
        Set<Integer> keys = this.map.keySet();
        ArrayList<Entity> matches = new ArrayList<>();
        for (int k : keys) {
            long offset = this.map.get(k);
            boolean match = false;
            Entity obj = new Entity();
            this.dataFile.seek(offset);
            obj.readFrom(this.dataFile, offset);

            switch(f) {
                case ID:
                    if (obj.getId() == (Integer) value) {
                        match = true;
                    }
                    break;
                case NAME:
                    if (obj.getCarName().equals(value)) {
                        match = true;
                    }
                    break;
                case HP:
                    if (obj.getHP() == (Integer) value) {
                        match = true;
                    }
                    break;
                case PRICE:
                    if (obj.getPrice() == (Double) value) {
                        match = true;
                    }
                    break;
                case AVAILABLE:
                    if (obj.getAvail() == (boolean) value) {
                        match = true;
                    }
                    break;
                default:
                    throw new IllegalArgumentException("[FILEDB] seekByValue: No such Entity field: " + f);
            }
            if (match) {
                matches.add(obj);
            }
        }
        this.dataFile.seek(this.dataFile.length());
        return matches;
    }

    public void edit(int id, Field f, Object newValue) throws IOException {
        /*
        1. if opened - good
        2. first and foremost, we need to get a field which have to be edited
        3. secondly, we have to get a value that will be written into the field
        4. need to seekById for fast editing, because we rewrite only one recording in a certain field. Otherwise, it would do a lot of extra job.
        5. DO NOT rewrite the id! It is Utility-only field with opportunity for user to seek by it, but not editing.
        */
       if (!IS_OPENED) {
            throw new IllegalArgumentException("Data base is not opened");
            }
        Long offset = this.map.get(id);
        if (offset == null) {
            throw new IllegalArgumentException("[FILEDB] edit: No such id: " + id);
            }
        Entity obj = new Entity();
        obj.readFrom(this.dataFile, offset);

        switch (f) {
        case NAME      -> obj.setCarName((String) newValue);
        case HP        -> obj.setHP((Integer) newValue);
        case PRICE     -> obj.setPrice((Double) newValue);
        case AVAILABLE -> obj.setAvail((Boolean) newValue);
        case ID        -> throw new IllegalArgumentException("[FILEDB] edit: ID is not editable");
        }
        dataFile.seek(offset);
        obj.writeTo(dataFile);
    }

    public String createBackupFile(String baseBackupDir) throws IOException {
        if (!IS_OPENED) {
            throw new IllegalStateException("Data base is not opened");
        }

        String timestamp = new SimpleDateFormat("yyyy-MM-dd__HH-mm-ss").format(new Date());
        File backupDir = new File(baseBackupDir, timestamp);
        
        if (!backupDir.exists()) {
            if (!backupDir.mkdirs()) {
                throw new IOException("[FILEDB] 'createBackupFile': Could not create backup directory: " + backupDir.getAbsolutePath());
            }
        }

        File dataBackup = new File(backupDir, new File(dataPath).getName());
        File idxBackup  = new File(backupDir, new File(mapPath).getName());

        File Data = new File(this.dataPath);
        File Idx = new File(this.mapPath);

        copyFileWithSync(Data, dataBackup); // source -> destination
        copyFileWithSync(Idx, idxBackup);

        System.out.println("[FILEDB] Backup created at: " + backupDir.getAbsolutePath());
        
        return backupDir.getAbsolutePath(); 
    }

    public void createBackupFile() throws IOException {
        createBackupFile("backups");
    }

    public void recoverFromBackup(String backupDirPath) throws IOException {
        File backupDir = new File(backupDirPath);
        File dataBackup = new File(backupDir, new File(dataPath).getName());
        File idxBackup  = new File(backupDir, new File(mapPath).getName());

        if (!dataBackup.exists() || !idxBackup.exists()) {
            throw new IOException("[FILEDDB] required .dat and .idx to do a backup");
        }

        this.dataFile.close();
        this.hashFile.close();
        IS_OPENED = false;

        File Data = new File(this.dataPath);
        File Idx = new File(this.mapPath);
        try {
            copyFileWithSync(dataBackup, Data); // source -> destination
            copyFileWithSync(idxBackup, Idx);
        } finally { // new files will open in any case
            this.dataFile = new RandomAccessFile(this.dataPath, "rw");
            this.hashFile = new RandomAccessFile(this.mapPath, "rw");
        }

        this.map.clear();
        openDataBase();
        System.out.println("[FILEDB]: Data Base is successfully recovered using this backup: " + backupDirPath);
    }

    public void show() {

    }

    public void importFrom() {

    }
}