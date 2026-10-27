import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public class Entity { // or maybe DataStorage? It seems more like a data storage.
    public final static int RECORD_SIZE = 82;
    public final static int ID_SIZE = 4;
    public final static int NAME_SIZE = 64;
    public final static int HP_SIZE = 4;
    public final static int PRICE_SIZE = 8;
    public final static int FLAG_SIZE = 1;    // summary 82 with utility 'deleted' flag;
    public final static int LAYOUTVER = 1; // version of layout: id name hp price avail deleted.

    private int id;
    private String carName;
    private int carHp;
    private double carPrice;
    private boolean carAvail;
    // util
    private boolean deleted;

    public Entity(int idx, String name, int hp, double price, boolean available) {
        if (name.getBytes(StandardCharsets.UTF_8).length > NAME_SIZE) {
            throw new IllegalArgumentException("[ENTITY]: (EXCEPTION) TOO LONG CAR NAME GIVEN");
        } 
        this.carName = name;
        this.carHp = hp;
        this.carPrice = price;
        this.carAvail = available;
        this.id = idx;
        this.deleted = false;
    }

    public Entity() {}

    public byte[] intoByte_(String str, int len) {
        byte[] result = new byte[len];
        byte[] source = str.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(source, 0, result, 0, Math.min(source.length, len));
        return result;
    }

    public void writeTo(RandomAccessFile raf) throws IOException {
        raf.writeInt(this.id);
        raf.write(intoByte_(carName, NAME_SIZE));
        raf.writeInt(carHp);
        raf.writeDouble(carPrice);
        raf.writeBoolean(carAvail);
        raf.writeBoolean(deleted);
    }

    public void readFrom(RandomAccessFile raf, long where) throws IOException {
        raf.seek(where);
        this.id = raf.readInt();
        byte[] temporary = new byte[NAME_SIZE];
        raf.read(temporary, 0, NAME_SIZE);
        this.carName = new String(temporary, StandardCharsets.UTF_8);
        this.carHp = raf.readInt();
        this.carPrice = raf.readDouble();
        this.carAvail = raf.readBoolean();
        this.deleted = raf.readBoolean();
        raf.seek(where + RECORD_SIZE);
    }
}