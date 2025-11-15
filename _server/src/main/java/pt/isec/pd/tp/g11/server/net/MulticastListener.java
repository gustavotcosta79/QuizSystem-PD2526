package pt.isec.pd.tp.g11.server.net;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;
import pt.isec.pd.tp.g11.server.db.DatabaseManager;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;

public class MulticastListener extends Thread {
    private final InetAddress multicastAddress;
    private final int port = 3030; // Porto fixo do enunciado
    private final DatabaseManager dbManager;
    private final int myDbPort; // Para saber se a msg fui eu que enviei

    public MulticastListener(InetAddress multicastAddress, DatabaseManager dbManager, int myDbPort) {
        this.multicastAddress = multicastAddress;
        this.dbManager = dbManager;
        this.myDbPort = myDbPort;
        setName("MulticastListener");
    }

    @Override
    public void run() {
        try (MulticastSocket socket = new MulticastSocket(port)) {
            // Juntar-se ao grupo (código moderno para Java mais recente, ou usar socket.joinGroup(address) se for Java antigo)
            socket.joinGroup(new java.net.InetSocketAddress(multicastAddress, port), NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));

            System.out.println("[MulticastListener] A escutar em " + multicastAddress + ":" + port);
            byte[] buffer = new byte[65535]; // Buffer grande para SQL

            while (!isInterrupted()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Deserializar
                // Nota: É preciso cortar o buffer para o tamanho exato recebido
                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                Object obj = SerializationUtils.deserialize(data);
                if (obj instanceof UDPMessage msg && msg.getType() == MessageType.SERVER_HEARTBEAT) {
                    processHeartbeat(msg);
                }
            }
        } catch (Exception e) {
            System.err.println("[MulticastListener] Erro: " + e.getMessage());
        }
    }

    private void processHeartbeat(UDPMessage msg) {
        String[] payload = msg.getPayload();
        if (payload == null || payload.length < 3) return;

        int senderDbPort = Integer.parseInt(payload[1]);
        int receivedVersion = Integer.parseInt(payload[2]);

        // 1. Ignorar as minhas próprias mensagens
        if (senderDbPort == myDbPort) return;

        // 2. Verificar se traz SQL (tamanho 4)
        if (payload.length == 4) {
            String sqlQuery = payload[3];
            int currentVersion = dbManager.getDbVersion();

            // 3. Verificar consistência da versão
            if (receivedVersion == currentVersion + 1) {
                System.out.println("[MulticastListener] Recebido update v" + receivedVersion + ". A aplicar...");
                dbManager.executeReplicaQuery(sqlQuery);
            } else if (receivedVersion > currentVersion + 1) {
                System.err.println("[MulticastListener] ERRO: Perdi sincronização! (Eu: " + currentVersion + ", Recebido: " + receivedVersion + ")");
                // TODO: Aqui o backup deveria reiniciar ou pedir full sync novamente
            }
        }
    }
}