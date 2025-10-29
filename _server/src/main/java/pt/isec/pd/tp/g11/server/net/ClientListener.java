package pt.isec.pd.tp.g11.server.net;

import java.net.ServerSocket;
import java.net.Socket;

public class ClientListener extends Thread {

    private final ServerSocket serverSocket;
    //private final DataManager dbManager;

    public ClientListener (ServerSocket serverSocket /*,DatabaseManager dbManager*/){
        this.serverSocket = serverSocket;
        //this.dbManager = dbManager;
    }

    @Override
    public void run () {
        System.out.println("[ClientListener] A escutar clientes no porto " + serverSocket.getLocalPort());
        try {
            while (!isInterrupted()) {
                // Fica bloqueado aqui até um cliente se ligar
                Socket clientSocket = serverSocket.accept();

                System.out.println("[ClientListener] Novo cliente ligado: " + clientSocket.getInetAddress());

                // TODO: Lançar uma thread para tratar do cliente
                // ClientHandler handler = new ClientHandler(clientSocket, dbManager);
                // new Thread(handler).start();
            }
        } catch (Exception e) {
            if (!serverSocket.isClosed()) {
                System.err.println("[ClientListener] Erro no listener de clientes: " + e.getMessage());
            }
        } finally {
            System.out.println("[ClientListener] Serviço parado.");
        }
    }
}
