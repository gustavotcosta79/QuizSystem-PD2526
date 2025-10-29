/*
 * Ficheiro: HeartbeatService.java
 * VERSÃO ATUALIZADA - Compatível com ServerConfig (3 argumentos)
 */
package pt.isec.pd.tp.g11.server.net;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.server.ServerConfig; // Importar a sua nova classe
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class HeartbeatService extends Thread {

    public static final int HEARTBEAT_INTERVAL_MS = 5000; // 5 segundos

    private final InetAddress directoryAddress;
    private final int directoryPort;
    private final int serverClientPort;
    private final int serverDbPort;
    private final InetAddress multicastAddress;
    private final int multicastPort = 3030; // Fixo, conforme PDF

    // TODO: private final DatabaseManager dbManager;

    private boolean isRunning = true;

    // Construtor atualizado para receber a Config
    public HeartbeatService(ServerConfig config, int serverClientPort, int serverDbPort /*, DatabaseManager dbManager */) {
        this.directoryAddress = config.getDirectoryAddress();
        this.directoryPort = config.getDirectoryPort();
        this.multicastAddress = config.getMulticastAddress();
        this.serverClientPort = serverClientPort;
        this.serverDbPort = serverDbPort;
        // this.dbManager = dbManager;

        setDaemon(true);
        setName("HeartbeatService");
    }

    public void stopHeartbeat() {
        this.isRunning = false;
        interrupt(); // Interrompe o Thread.sleep()
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {

            while (isRunning) {
                try {
                    // TODO: Obter a versão da BD
                    // int dbVersion = dbManager.getDbVersion();
                    int dbVersion = 0; // Placeholder

                    // 2. Criar o payload (consistente com o ServerListManager)
                    String[] payload = {
                            String.valueOf(serverClientPort),
                            String.valueOf(serverDbPort),
                            String.valueOf(dbVersion) // Adiciona a versão da BD
                    };
                    UDPMessage msg = new UDPMessage(MessageType.SERVER_HEARTBEAT, payload);

                    byte[] data = SerializationUtils.serialize(msg);

                    // 4. Enviar pacote UNICAST (para o DirectoryService)
                    //
                    DatagramPacket unicastPacket = new DatagramPacket(data, data.length, directoryAddress, directoryPort);
                    socket.send(unicastPacket);

                    // 5. Enviar pacote MULTICAST (para os outros Servidores)
                    //
                    DatagramPacket multicastPacket = new DatagramPacket(data, data.length, multicastAddress, multicastPort);
                    socket.send(multicastPacket);

                    // 6. Esperar pela resposta (APENAS do diretório)
                    //
                    byte[] buffer = new byte[4096];
                    DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                    socket.setSoTimeout(2000);

                    socket.receive(responsePacket);

                    UDPMessage response = (UDPMessage) SerializationUtils.deserialize(responsePacket.getData());
                    // TODO: Processar a resposta do diretório (saber quem é o principal)
                    // String[] primaryInfo = response.getPayload();
                    // System.out.println("[Heartbeat] Resposta do Dir: O primário é " + primaryInfo[0]);

                    // 7. Dormir 5 segundos
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);

                } catch (InterruptedException e) {
                    if (isRunning) System.err.println("[Heartbeat] Interrompido.");
                } catch (java.net.SocketTimeoutException e) {
                    System.err.println("[Heartbeat] Diretório não respondeu ao heartbeat.");
                } catch (Exception e) {
                    System.err.println("[HeartbeatService] Erro no loop: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[HeartbeatService] Erro fatal (não conseguiu abrir socket): " + e.getMessage());
        }
        System.out.println("[Heartbeat] Serviço parado.");
    }
}