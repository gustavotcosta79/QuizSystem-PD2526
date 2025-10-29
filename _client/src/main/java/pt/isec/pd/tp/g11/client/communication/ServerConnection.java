/*
 * Ficheiro: ServerConnection.java
 * [cite_start]Objetivo: Componente de Comunicação[cite: 173].
 * Responsabilidade: Gerir toda a comunicação de rede (UDP e TCP).
 * A Vista (ConsoleUI) chama métodos desta classe para
 * executar ações de rede.
 */
package pt.isec.pd.tp.g11.client.communication;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.TCPMessage;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.common.model.User;
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

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

    public User login(String email, String password) {
        // Se já tivermos uma ligação, tentar reutilizá-la?
        // Por agora, vamos assumir que o login é a primeira ação TCP.
        if (tcpSocket != null && !tcpSocket.isClosed()) {
            System.err.println("[Comunicação] Já existe uma ligação TCP ativa.");
            // Poderíamos tentar fazer logout primeiro, ou simplesmente falhar.
            return null;
        }

        if (serverIp == null) {
            System.err.println("Erro: Servidor principal não encontrado via UDP.");
            return null;
        }

        try {
            // 1. Estabelecer a ligação TCP permanente (NÃO usar try-with-resources)
            System.out.println("[Comunicação] A ligar (TCP) a " + serverIp + ":" + serverPort + "...");
            this.tcpSocket = new Socket(serverIp, serverPort);
            // IMPORTANTE: Criar o Output stream PRIMEIRO
            this.out = new ObjectOutputStream(tcpSocket.getOutputStream());
            this.in = new ObjectInputStream(tcpSocket.getInputStream());
            System.out.println("[Comunicação] Ligação TCP estabelecida.");

            // 2. Criar e enviar a mensagem de Login
            String[] credentials = {email, password};
            TCPMessage loginRequest = new TCPMessage(MessageType.LOGIN_REQUEST, credentials);
            System.out.println("[Comunicação] A enviar pedido de LOGIN...");
            out.writeObject(loginRequest);
            out.flush(); // Garante que a mensagem é enviada

            // 3. Esperar pela resposta do servidor (LOGIN_SUCCESS ou LOGIN_FAILED)
            System.out.println("[Comunicação] A aguardar resposta do servidor...");
            Object responseObj = in.readObject(); // Bloqueia até receber resposta

            if (!(responseObj instanceof TCPMessage)) {
                System.err.println("[Comunicação] Resposta inválida do servidor.");
                closeConnection(); // Fechar ligação
                return null;
            }

            TCPMessage response = (TCPMessage) responseObj;

            // 4. Processar a resposta
            if (response.getType() == MessageType.LOGIN_SUCCESS) {
                if (response.getPayload() instanceof User) {
                    User user = (User) response.getPayload();
                    System.out.println("[Comunicação] Login bem-sucedido para: " + user.getEmail());

                    // TODO: Iniciar a thread de escuta de notificações assíncronas
                    // this.notificationListener = new NotificationListener(in);
                    // this.notificationListener.start();

                    return user; // SUCESSO! Devolve o objeto User
                } else {
                    System.err.println("[Comunicação] Payload inválido para LOGIN_SUCCESS.");
                    closeConnection();
                    return null;
                }
            } else {
                // Login falhou (ex: LOGIN_FAILED ou outro erro)
                String errorMsg = (response.getPayload() instanceof String) ? (String) response.getPayload() : "Erro desconhecido.";
                System.err.println("[Comunicação] Login falhou: " + errorMsg);
                closeConnection(); // Fechar ligação em caso de falha
                return null;
            }

        } catch (Exception e) {
            System.err.println("[Comunicação] Erro durante o login: " + e.getMessage());
            closeConnection(); // Garante que fecha a ligação em caso de erro
            return null;
        }
    }

    /** Fecha a ligação TCP e os streams */
    public void closeConnection() {
        // TODO: Enviar mensagem de LOGOUT para o servidor antes de fechar?
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        } catch (Exception e) { /* ignorar */ }
        System.out.println("[Comunicação] Ligação TCP fechada.");
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