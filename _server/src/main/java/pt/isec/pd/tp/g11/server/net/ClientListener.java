package pt.isec.pd.tp.g11.server.net;

import pt.isec.pd.tp.g11.server.db.DatabaseManager;

import java.net.ServerSocket;
import java.net.Socket;

public class ClientListener extends Thread {

    private final ServerSocket serverSocket;
    private final DatabaseManager dbManager; // Vais precisar de passar isto

    public ClientListener (ServerSocket serverSocket ,DatabaseManager dbManager){
        this.serverSocket = serverSocket;
         this.dbManager = dbManager;
        setName("ClientListener"); // Boa prática
    }

    // O método stopListener() que tinhas pode ser útil no futuro
    // public void stopListener() { ... }

    @Override
    public void run () {
        System.out.println("[ClientListener] A escutar clientes no porto " + serverSocket.getLocalPort());
        try {
            while (!isInterrupted() && !serverSocket.isClosed()) { // Melhor condição de loop
                // Fica bloqueado aqui até um cliente se ligar
                Socket clientSocket = serverSocket.accept();

                System.out.println("[ClientListener] Novo cliente ligado: " + clientSocket.getInetAddress());

                // Lança uma thread para tratar do cliente
                ClientHandler handler = new ClientHandler(clientSocket ,dbManager );
                handler.start(); // Inicia a thread ClientHandler
            }
        } catch (java.net.SocketException e) {
            System.out.println("[ClientListener] Socket fechado, a parar."); // Normal quando o servidor fecha
        } catch (Exception e) {
            System.err.println("[ClientListener] Erro no listener de clientes: " + e.getMessage());
        } finally {
            System.out.println("[ClientListener] Serviço parado.");
        }
    }
}