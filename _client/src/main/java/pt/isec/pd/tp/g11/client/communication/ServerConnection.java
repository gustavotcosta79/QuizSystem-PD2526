/*
 * Ficheiro: ServerConnection.java
 * [cite_start]Objetivo: Componente de Comunicação[cite: 173].
 * Responsabilidade: Gerir toda a comunicação de rede (UDP e TCP).
 * A Vista (ConsoleUI) chama métodos desta classe para
 * executar ações de rede.
 */
package pt.isec.pd.tp.g11.client.communication;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ServerConnection {

    private static final int DIRECTORY_RESPONSE_TIMEOUT_MS = 3000;

    // Info da Diretoria (do arranque)
    private final InetAddress dirAddress;
    private final int dirPort;

    // Info do Servidor (descoberto via UDP)
    private String serverIp;
    private int serverPort;

    // TODO: A ligação TCP principal e os ObjectStreams
    // private Socket tcpSocket;
    // private ObjectOutputStream out;
    // private ObjectInputStream in;

    public ServerConnection(String dirInfo) throws Exception {
        String[] parts = dirInfo.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Formato inválido para <dir_ip:dir_port>");

        this.dirAddress = InetAddress.getByName(parts[0]);
        this.dirPort = Integer.parseInt(parts[1]);
    }

    /**
     * Contacta o Serviço de Diretoria (UDP) para descobrir
     * o IP/Porto do servidor principal.
     * @return true se bem-sucedido, false se falhar.
     */
    public boolean findServer() {
        System.out.println("[Comunicação] A contactar diretoria em " + dirAddress.getHostAddress() + ":" + dirPort);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(DIRECTORY_RESPONSE_TIMEOUT_MS);

            // 1. Criar e enviar pedido
            UDPMessage requestMsg = new UDPMessage(MessageType.CLIENT_REQUEST_SERVER);
            byte[] sendData = SerializationUtils.serialize(requestMsg);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, dirAddress, dirPort);
            socket.send(sendPacket);

            // 2. Receber resposta
            byte[] receiveBuffer = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            // 3. Processar resposta
            UDPMessage responseMsg = (UDPMessage) SerializationUtils.deserialize(receivePacket.getData());

            if (responseMsg.getType() == MessageType.CLIENT_RESPONSE_SERVER) {
                String[] payload = responseMsg.getPayload();
                this.serverIp = payload[0];
                this.serverPort = Integer.parseInt(payload[1]); // Este é o clientPort do servidor
                System.out.println("[Comunicação] Servidor encontrado em: " + serverIp + ":" + serverPort);
                return true;
            } else {
                System.err.println("[Comunicação] Diretoria respondeu com erro: " + responseMsg.getPayload()[0]);
                return false;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[Comunicação] Diretoria não respondeu (timeout).");
            return false;
        } catch (Exception e) {
            System.err.println("[Comunicação] Erro ao procurar servidor: " + e.getMessage());
            return false;
        }
    }

    /**
     * Tenta estabelecer uma ligação TCP com o servidor
     * (apenas para teste do "Esqueleto").
     */
    public void testTCPConnection() {
        if (serverIp == null) {
            System.out.println("Erro: Servidor ainda não foi encontrado. Tente a opção 1 primeiro.");
            return;
        }

        System.out.println("[Comunicação] A ligar (TCP) a " + serverIp + ":" + serverPort + "...");
        try (Socket testSocket = new Socket(serverIp, serverPort)) {
            System.out.println("[Comunicação] LIGADO! Ligação TCP estabelecida.");
            System.out.println("(O cliente fechou a ligação, como esperado no teste.)");
        } catch (Exception e) {
            System.err.println("[Comunicação] Falha ao ligar ao servidor: " + e.getMessage());
        }
    }

    // TODO: Métodos futuros que a Vista irá chamar
    /*
    public User login(String email, String password) {
        // 1. Estabelecer a ligação TCP permanente (tcpSocket)
        // 2. Enviar um TCPMessage de LOGIN
        // 3. Receber um TCPMessage de resposta
        // 4. Iniciar a thread de escuta de notificações (se o login for bom)
        // 5. Devolver o objeto User (ou null se falhar)
        return null;
    }
    */
}