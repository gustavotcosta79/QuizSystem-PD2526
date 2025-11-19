/*
 * Ficheiro: MainServer.java
 * VERSÃO COM DBManager, ClientListener e Sincronização de BD (DbSync) integrados.
 */
package pt.isec.pd.tp.g11.server;

// Imports do projeto
import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.server.net.ClientListener;
import pt.isec.pd.tp.g11.server.net.DbSyncListener; // Novo Listener
import pt.isec.pd.tp.g11.server.net.HeartbeatService;
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;
import pt.isec.pd.tp.g11.server.db.DatabaseManager;
import pt.isec.pd.tp.g11.server.net.MulticastListener;

// Imports Java
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class MainServer {

    private static final int DIRECTORY_RESPONSE_TIMEOUT_MS = 5000;

    public static void main(String[] args) {
        ServerConfig config;
        ServerSocket clientSocket = null; // Socket TCP para Clientes
        ServerSocket dbSyncSocket = null; // Socket TCP para Sincronização de BD
        DatabaseManager dbManager = null;
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
            if (!dbManager.connect()) {
                System.err.println("[MainServer] Falha ao ligar/criar a base de dados. A terminar.");
                return;
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
                    System.out.println("[MainServer] Registado com sucesso. O primário reportado é: " + primaryServerInfo[0] + " (Porto BD: " + primaryServerInfo[1] + ")");
                } else {
                    System.err.println("[MainServer] Registo falhado: " + (responseMsg.getPayload() != null ? responseMsg.getPayload()[0] : "ERRO"));
                    if (dbManager != null) dbManager.disconnect();
                    return;
                }
            } catch (SocketTimeoutException e) {
                System.err.println("[MainServer] O serviço de diretoria não respondeu (timeout). A terminar.");
                if (dbManager != null) dbManager.disconnect();
                return;
            }

            // 5. VERIFICAR SE ESTE SERVIDOR É O PRIMÁRIO OU BACKUP
            boolean isPrimary = false;

            // Compara o porto reportado pela diretoria com o meu porto local
            if (Integer.parseInt(primaryServerInfo[1]) == dbPort) {
                isPrimary = true;
            }

            if (isPrimary) {
                System.out.println("[MainServer] >>> ESTE SERVIDOR É O PRIMÁRIO <<<");


            } else {
                System.out.println("[MainServer] >>> ESTE SERVIDOR É BACKUP <<<");
                System.out.println("[MainServer] A sincronizar com o Primário (" + primaryServerInfo[0] + ":" + primaryServerInfo[1] + ")...");

                // === LÓGICA DE BACKUP: DOWNLOAD DA BD ===

                // 1. Desligar da BD local temporariamente (para poder substituir o ficheiro)
                dbManager.disconnect();
                String localDbPath = dbManager.getDbFilePath();

                boolean syncSuccess = false;

                // Conecta ao Primário no IP e Porto de BD indicados pela diretoria
                try (Socket socket = new Socket(primaryServerInfo[0], Integer.parseInt(primaryServerInfo[1]));
                     InputStream in = socket.getInputStream();
                     FileOutputStream fileOut = new FileOutputStream(localDbPath)) {

                    System.out.println("[MainServer] A descarregar base de dados...");

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        fileOut.write(buffer, 0, bytesRead);
                    }

                    System.out.println("[MainServer] Download da base de dados concluído!");
                    syncSuccess = true;

                } catch (Exception e) {
                    System.err.println("[MainServer] FALHA CRÍTICA AO SINCRONIZAR COM PRIMÁRIO: " + e.getMessage());
                    // Se falhar a sincronização inicial, o servidor deve terminar para evitar inconsistências
                    if (clientSocket != null) clientSocket.close();
                    if (dbSyncSocket != null) dbSyncSocket.close();
                    return;
                }

                // 2. Voltar a ligar à BD (agora com os dados novos copiados do primário)
                if (syncSuccess) {
                    if (!dbManager.connect()) {
                        System.err.println("[MainServer] Erro ao religar à BD após sincronização.");
                        return;
                    }
                    System.out.println("[MainServer] BD recarregada com sucesso e pronta.");
                }
            }

            // 6. INICIAR OS SERVIÇOS DE BACKGROUND (THREADS)

            // --- ALTERAÇÃO AQUI: O DbSyncListener arranca SEMPRE ---
            // Assim, se este backup virar Primary, já está pronto a enviar a BD.
            String currentDbPath = dbManager.getDbFilePath();
            if (currentDbPath != null) {
                System.out.println("[MainServer] A ativar serviço de envio de BD (DbSyncListener)...");
                DbSyncListener dbSyncListener = new DbSyncListener(dbSyncSocket, currentDbPath);
                dbSyncListener.start();
            }

            // Serviço de Heartbeat (envia UDP para diretoria e Multicast para cluster)
            HeartbeatService heartbeat = new HeartbeatService(config, clientPort, dbPort /*, dbManager */);
            heartbeat.start();

            // Serviço de Escuta de Clientes TCP
            System.out.println("[MainServer] A iniciar ClientListener...");
            ClientListener clientListener = new ClientListener(clientSocket, dbManager,heartbeat);
            clientListener.start();

            // Iniciar MulticastListener
            MulticastListener mcListener = new MulticastListener(config.getMulticastAddress(), dbManager, dbPort);
            mcListener.start();

            System.out.println("[MainServer] Servidor operacional e pronto a receber clientes.");

            // Shutdown Hook para fechar recursos ordenadamente
            DatabaseManager finalDbManager = dbManager;
            ServerSocket finalClientSocket = clientSocket;
            ServerSocket finalDbSyncSocket = dbSyncSocket;
            ServerConfig finalConfig = config; // <--- IMPORTANTE: Capturar a config para usar no lambda

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[MainServer] A desligar servidor (Shutdown Hook)...");

                // 1. ENVIAR O PEDIDO DE UNREGISTER À DIRETORIA
                try (DatagramSocket socket = new DatagramSocket()) {
                    UDPMessage msg = new UDPMessage(MessageType.SERVER_UNREGISTER);
                    byte[] data = SerializationUtils.serialize(msg);

                    DatagramPacket packet = new DatagramPacket(
                            data,
                            data.length,
                            finalConfig.getDirectoryAddress(),
                            finalConfig.getDirectoryPort()
                    );

                    socket.send(packet);
                    System.out.println("[MainServer] Pedido de remoção enviado à diretoria.");

                } catch (Exception e) {
                    System.err.println("[MainServer] Erro ao enviar unregister: " + e.getMessage());
                }

                // 2. FECHAR SOCKETS TCP (Limpeza Local)
                try {
                    if (finalClientSocket != null && !finalClientSocket.isClosed()) finalClientSocket.close();
                    if (finalDbSyncSocket != null && !finalDbSyncSocket.isClosed()) finalDbSyncSocket.close();
                } catch (Exception e) { System.err.println("[Shutdown Hook] Erro ao fechar sockets: " + e.getMessage()); }

                // 3. FECHAR BD
                if (finalDbManager != null) {
                    finalDbManager.disconnect();
                }
                System.out.println("[MainServer] Servidor desligado.");
            }));

        } catch (Exception e) {
            System.err.println("[MainServer] Erro fatal durante o arranque: " + e.getMessage());
            e.printStackTrace();

            try {
                if (clientSocket != null) clientSocket.close();
                if (dbSyncSocket != null) dbSyncSocket.close();
                if (dbManager != null) dbManager.disconnect();
            } catch (Exception ex) {  }

        System.out.println("[MainServer] A terminar processo (System.exit).");
        System.exit(1); // Garante que o Heartbeat e todas as threads morrem
        }
    }
}