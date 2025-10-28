/*
 * Ficheiro: DirectoryService.java
 * Objetivo: Thread principal de escuta do Serviço de Diretoria.
 * Responsabilidade: Ficar num loop infinito (while(true)) à escuta
 * no porto UDP. Utiliza a classe SerializationUtils
 * para deserializar Objetos UDPMessage e serializar
 * as respostas.
 */

package pt.isec.pd.tp.g11.directory_service.net;

// REMOVEMOS os imports de java.io.* porque já não são precisos aqui

import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;
import pt.isec.pd.tp.g11.directory_service.logic.ServerListManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

// IMPORTA A TUA NOVA CLASSE UTILS
// (Certifica-te que o package está correto)


public class DirectoryService extends Thread {

    private final int port;
    private static final int BUFFER_SIZE = 4096; // Buffer para objetos serializados

    private final ServerListManager serverManager;

    public DirectoryService(int port) {
        this.port = port;
        this.serverManager = new ServerListManager();
        this.serverManager.start();
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Serviço de Diretoria a escutar em " + socket.getLocalAddress() + ":" + port);

            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                try {
                    // 1. Receber o PACOTE de bytes
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    // 2. DESERIALIZAR (Agora com o teu Utils)
                    // Nota: temos de copiar os dados recebidos para um array
                    // com o tamanho exato, para evitar dados extra do buffer.
                    byte[] receivedData = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), packet.getOffset(), receivedData, 0, packet.getLength());

                    UDPMessage receivedMsg = (UDPMessage) SerializationUtils.deserialize(receivedData);

                    InetAddress senderAddress = packet.getAddress();
                    int senderPort = packet.getPort();
                    System.out.println("Recebido de " + senderAddress.getHostAddress() + ":" + senderPort + " -> " + receivedMsg.getType());

                    // 3. Processar o objeto na LÓGICA
                    UDPMessage responseMsg = serverManager.processRequest(receivedMsg, senderAddress);

                    // 4. SERIALIZAR a resposta (Agora com o teu Utils)
                    byte[] responseData = SerializationUtils.serialize(responseMsg);

                    // 5. Enviar o PACOTE de bytes de resposta
                    DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, senderAddress, senderPort);
                    socket.send(responsePacket);

                    System.out.println("Respondido para " + senderAddress.getHostAddress() + ":" + senderPort + " -> " + responseMsg.getType());

                } catch (Exception e) {
                    System.err.println("Erro ao processar pacote: " + e.getMessage());
                    e.printStackTrace(); // Útil para debugging
                }
            }
        } catch (Exception e) {
            System.err.println("Erro fatal no Serviço de Diretoria: " + e.getMessage());
            e.printStackTrace();
        }
    }
}