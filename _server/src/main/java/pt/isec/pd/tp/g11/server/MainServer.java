/*
 * Ficheiro: MainServer.java
 * VERSÃO COM DBManager e ClientListener integrados.
 */
package pt.isec.pd.tp.g11.server;

// Imports do projeto
import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.server.net.ClientListener; // Importar o Listener
import pt.isec.pd.tp.g11.server.net.HeartbeatService;
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;
import pt.isec.pd.tp.g11.server.db.DatabaseManager; // Importar o DB Manager

// Imports Java
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
        DatabaseManager dbManager = null; // Variável para o gestor da BD
        int clientPort;
        int dbPort;
        String[] primaryServerInfo;

        try {
            // 1. Ler os argumentos da linha de comando
            config = new ServerConfig(args);
            System.out.println("[MainServer] Configuração carregada. A ligar à diretoria em " +
                    config.getDirectoryAddress().getHostAddress() + ":" + config.getDirectoryPort());

            // 2. INICIAR O DATABASE MANAGER
            System.out.println("[MainServer] A inicializar Database Manager para: " + config.getDbPath());
            dbManager = new DatabaseManager(config.getDbPath());
            if (!dbManager.connect()) { // Tenta ligar e criar tabelas
                System.err.println("[MainServer] Falha ao ligar/criar a base de dados. A terminar.");
                return; // Termina se a BD falhar
            }

            // 3. Abrir os ServerSockets TCP em portos automáticos
            clientSocket = new ServerSocket(0);
            dbSyncSocket = new ServerSocket(0);
            clientPort = clientSocket.getLocalPort();
            dbPort = dbSyncSocket.getLocalPort();

            System.out.println("[MainServer] A escutar Clientes em TCP:" + clientPort);
            System.out.println("[MainServer] A escutar BD Sync em TCP:" + dbPort);

            // 4. FAZER O REGISTO SÍNCRONO NO SERVIÇO DE DIRETORIA
            System.out.println("[MainServer] A registar-se no diretório...");
            String[] registerPayload = { String.valueOf(clientPort), String.valueOf(dbPort) };
            UDPMessage registerMsg = new UDPMessage(MessageType.SERVER_REGISTER, registerPayload); //

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(DIRECTORY_RESPONSE_TIMEOUT_MS);
                byte[] sendData = SerializationUtils.serialize(registerMsg); //
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, config.getDirectoryAddress(), config.getDirectoryPort());
                socket.send(sendPacket);

                byte[] receiveData = new byte[4096];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                UDPMessage responseMsg = (UDPMessage) SerializationUtils.deserialize(receiveData); //

                if (responseMsg.getType() == MessageType.SERVER_REGISTER_OK) { //
                    primaryServerInfo = responseMsg.getPayload();
                    System.out.println("[MainServer] Registado com sucesso. O primário reportado é: " + primaryServerInfo[0] + " (Porto BD: " + primaryServerInfo[1] + ")");
                } else {
                    System.err.println("[MainServer] Registo falhado: " + (responseMsg.getPayload() != null ? responseMsg.getPayload()[0] : "ERRO"));
                    if (dbManager != null) dbManager.disconnect();
                    return;
                }
            } catch (SocketTimeoutException e) {
                System.err.println("[MainServer] O serviço de diretoria não respondeu (timeout). A terminar."); //
                if (dbManager != null) dbManager.disconnect();
                return;
            }

            // 5. VERIFICAR SE ESTE SERVIDOR É O PRIMÁRIO OU BACKUP
            String myIp = "127.0.0.1"; // Usar IP de loopback para testes locais
            if (primaryServerInfo[0].equals(myIp) && Integer.parseInt(primaryServerInfo[1]) == dbPort) { // Compara com o dbPort recebido
                System.out.println("[MainServer] Este servidor É O PRIMÁRIO.");
                // TODO: Lógica adicional para primário (ex: garantir que a BD existe)
            } else {
                System.out.println("[MainServer] Este servidor é um BACKUP do primário em " + primaryServerInfo[0] + ":" + primaryServerInfo[1]);
                System.out.println("[MainServer] TODO: Implementar a sincronização da BD via TCP a partir do primário."); //
                // Se a sincronização falhar, deve terminar e informar a diretoria
            }

            // 6. INICIAR OS SERVIÇOS DE BACKGROUND (THREADS)

            // Serviço de Heartbeat (envia UDP para diretoria e Multicast para cluster)
            HeartbeatService heartbeat = new HeartbeatService(config, clientPort, dbPort /*, dbManager */); // Passar dbManager para incluir versão da BD
            heartbeat.start();

            // Serviço de Escuta de Clientes TCP (mantém o servidor vivo)
            System.out.println("[MainServer] A iniciar ClientListener...");
            ClientListener clientListener = new ClientListener(clientSocket, dbManager); // Passa o dbManager
            clientListener.start();

            // TODO: Serviço de Escuta para Sincronização de BD TCP
            // DbSyncListener dbSyncListener = new DbSyncListener(dbSyncSocket, dbManager);
            // dbSyncListener.start();

            System.out.println("[MainServer] Servidor operacional e pronto a receber clientes.");

            // Adicionar um Shutdown Hook para fechar recursos ordenadamente
            DatabaseManager finalDbManager = dbManager; // Variável final para lambda
            ServerSocket finalClientSocket = clientSocket;
            ServerSocket finalDbSyncSocket = dbSyncSocket;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[MainServer] A desligar servidor (Shutdown Hook)...");
                // TODO: Enviar SERVER_UNREGISTER para a diretoria
                try {
                    if (finalClientSocket != null && !finalClientSocket.isClosed()) finalClientSocket.close();
                    if (finalDbSyncSocket != null && !finalDbSyncSocket.isClosed()) finalDbSyncSocket.close();
                } catch (Exception e) { System.err.println("[Shutdown Hook] Erro ao fechar sockets: " + e.getMessage()); }

                if (finalDbManager != null) {
                    finalDbManager.disconnect();
                }
                System.out.println("[MainServer] Servidor desligado.");
            }));

        } catch (Exception e) {
            System.err.println("[MainServer] Erro fatal durante o arranque: " + e.getMessage());
            e.printStackTrace();

            // Tentativa final de fechar recursos em caso de erro grave no arranque
            try {
                if (clientSocket != null) clientSocket.close();
                if (dbSyncSocket != null) dbSyncSocket.close();
                if (dbManager != null) dbManager.disconnect();
            } catch (Exception ex) { /* ignorar erros durante o fecho de emergência */ }
        }
        // A thread 'main' termina aqui, mas as threads ClientListener e Heartbeat continuam a correr.
    }
}