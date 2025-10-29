/*
 * Ficheiro: MainServer.java
 * VERSÃO ATUALIZADA - Compatível com ServerConfig (3 argumentos)
 */
package pt.isec.pd.tp.g11.server; // Package da sua estrutura

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.server.net.HeartbeatService; // Package da sua estrutura
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

public class MainServer {

    private static final int DIRECTORY_RESPONSE_TIMEOUT_MS = 5000;

    public static void main(String[] args) {
        ServerConfig config;
        ServerSocket clientSocket = null; // Declarar fora para se manterem vivos
        ServerSocket dbSyncSocket = null; // Declarar fora
        int clientPort;
        int dbPort;
        String[] primaryServerInfo; //

        try {
            // 1. Ler os argumentos
            config = new ServerConfig(args);
            System.out.println("[MainServer] Configuração carregada. A ligar à diretoria em " +
                    config.getDirectoryAddress().getHostAddress() + ":" + config.getDirectoryPort());

            // 2. Abrir os ServerSockets TCP em portos automáticos
            clientSocket = new ServerSocket(0);
            dbSyncSocket = new ServerSocket(0);
            clientPort = clientSocket.getLocalPort();
            dbPort = dbSyncSocket.getLocalPort();

            System.out.println("[MainServer] A escutar Clientes em TCP:" + clientPort);
            System.out.println("[MainServer] A escutar BD Sync em TCP:" + dbPort);

            // 3. FAZER O REGISTO SÍNCRONO (A tua lógica está perfeita)
            System.out.println("[MainServer] A registar-se no diretório...");
            String[] registerPayload = { String.valueOf(clientPort), String.valueOf(dbPort) };
            UDPMessage registerMsg = new UDPMessage(MessageType.SERVER_REGISTER, registerPayload);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(DIRECTORY_RESPONSE_TIMEOUT_MS);
                byte[] sendData = SerializationUtils.serialize(registerMsg);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, config.getDirectoryAddress(), config.getDirectoryPort());
                socket.send(sendPacket);

                byte[] receiveData = new byte[4096];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                UDPMessage responseMsg = (UDPMessage) SerializationUtils.deserialize(receiveData);

                if (responseMsg.getType() == MessageType.SERVER_REGISTER_OK) {
                    primaryServerInfo = responseMsg.getPayload();
                    System.out.println("[MainServer] Registado com sucesso. O primário é: " + primaryServerInfo[0]);
                } else {
                    System.err.println("[MainServer] Registo falhado: " + (responseMsg.getPayload() != null ? responseMsg.getPayload()[0] : "ERRO"));
                    return;
                }
            } catch (SocketTimeoutException e) {
                System.err.println("[MainServer] O serviço de diretoria não respondeu. A terminar.");
                return;
            }

            // 4. VERIFICAR SE SOMOS PRIMÁRIOS E SINCRONIZAR BD (A tua lógica está perfeita)
            String myIp = InetAddress.getLocalHost().getHostAddress(); // Nota: Pode falhar em algumas configs de rede
            if (primaryServerInfo[0].equals(myIp) && Integer.parseInt(primaryServerInfo[1]) == clientPort) {
                System.out.println("[MainServer] Este servidor é o PRIMÁRIO.");
                // TODO: Iniciar a BD (e criar se não existir)
            } else {
                System.out.println("[MainServer] Este servidor é um BACKUP.");
                System.out.println("[MainServer] TODO: Sincronizar a BD com o primário.");
            }

            // 5. Iniciar os serviços de background
            HeartbeatService heartbeat = new HeartbeatService(config, clientPort, dbPort);
            heartbeat.start();

            // 6. Iniciar a thread que escuta clientes
            // ** CORREÇÃO: Passar o socket que continua aberto **
            ///
            //System.out.println("[MainServer] A iniciar ClientListener...");
            //ClientListener clientListener = new ClientListener(clientSocket);
            //clientListener.start(); // <-- Isto vai manter o servidor vivo
            ///
            // 7. TODO: Iniciar a thread que escuta pedidos de BD
            // DbSyncListener dbSyncListener = new DbSyncListener(dbSyncSocket);
            // dbSyncListener.start();

            System.out.println("[MainServer] Servidor operacional.");
            // REMOVEMOS O Thread.sleep(20000) porque o ClientListener
            // já mantém o servidor vivo.
            Thread.sleep(20000); // 20 segundos


        } catch (Exception e) {
            System.err.println("[MainServer] Erro fatal ao arrancar: " + e.getMessage());
            e.printStackTrace();

            // Garantir que os sockets fecham em caso de erro no arranque
            try {
                if (clientSocket != null) clientSocket.close();
                if (dbSyncSocket != null) dbSyncSocket.close();
            } catch (Exception ex) { /* ignorar */ }
        }
    }
}