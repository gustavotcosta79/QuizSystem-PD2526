package pt.isec.pd.tp.g11.common.utils;

import java.io.*;

public class SerializationUtils {

    //Serializa um objeto para um array de bytes.
    public static byte[] serialize(Serializable obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return bos.toByteArray();
        }
    }

    //Deserializa um array de bytes para um objeto.
    public static Object deserialize (byte [] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream in = new ObjectInputStream(bis)){
            return in.readObject();
        }
    }
}
