package pt.isec.pd.tp.g11.server.net;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;

public class DbSyncListener extends Thread {

    private final ServerSocket serverSocket;
    private final String dbFilePath; // Caminho completo para o ficheiro .db

    public DbSyncListener(ServerSocket serverSocket, String dbFilePath) {
        this.serverSocket = serverSocket;
        this.dbFilePath = dbFilePath;
        setName("DbSyncListener");
    }

    @Override
    public void run() { 
        System.out.println("[DbSyncListener] A aceitar pedidos de sincronização de BD no porto " + serverSocket.getLocalPort());
        try {
            while (!isInterrupted() && !serverSocket.isClosed()) {
                // 1. Esperar que um servidor Backup se ligue
                Socket backupSocket = serverSocket.accept();

                System.out.println("[DbSyncListener] Pedido de sincronização recebido de: " + backupSocket.getInetAddress());

                // 2. Lançar uma thread para enviar o ficheiro (para não bloquear este listener)
                new DbSyncHandler(backupSocket, dbFilePath).start();
            }
        } catch (Exception e) {
            if (!serverSocket.isClosed()) {
                System.err.println("[DbSyncListener] Erro: " + e.getMessage());
            }
        }
    }
}