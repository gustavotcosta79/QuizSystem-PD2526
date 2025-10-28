/*
 * Ficheiro: MainServer.java
 * Objetivo: Ponto de entrada (main) para a aplicação "Servidor".
 * Responsabilidade: Ler os argumentos de arranque, abrir os portos
 * TCP (para clientes e BD) e iniciar os serviços
 * (Heartbeat, ClientListener, etc.)
 */
package pt.isec.pd.tp.g11.server;

import pt.isec.pd.tp.g11.server.net.HeartbeatService;

import java.net.ServerSocket;

public class MainServer {

    public static void main(String[] args) {
        ServerConfig config;

        try {
            // 1. Ler os argumentos da linha de comando
            config = new ServerConfig(args);
            System.out.println("[MainServer] Configuração carregada. A ligar à diretoria em " +
                    config.getDirectoryAddress().getHostAddress() + ":" + config.getDirectoryPort());

            // 2. Abrir os ServerSockets TCP em portos automáticos
            // O '0' diz ao SO para escolher um porto livre
            ServerSocket clientSocket = new ServerSocket(0);
            ServerSocket dbSyncSocket = new ServerSocket(0);

            int clientPort = clientSocket.getLocalPort();
            int dbPort = dbSyncSocket.getLocalPort();

            System.out.println("[MainServer] A escutar Clientes em TCP:" + clientPort);
            System.out.println("[MainServer] A escutar BD Sync em TCP:" + dbPort);

            // 3. Iniciar o serviço de Heartbeat
            HeartbeatService heartbeat = new HeartbeatService(
                    config.getDirectoryAddress(),
                    config.getDirectoryPort(),
                    clientPort,
                    dbPort
            );
            heartbeat.start();

            // 4. TODO: Iniciar a thread que escuta clientes
            // ClientListener clientListener = new ClientListener(clientSocket);
            // clientListener.start();

            // 5. TODO: Iniciar a thread que escuta pedidos de BD
            // DbSyncListener dbSyncListener = new DbSyncListener(dbSyncSocket);
            // dbSyncListener.start();

            System.out.println("[MainServer] Servidor arrancado e a registar-se...");
            Thread.sleep(20000); // 20 segundos

        } catch (Exception e) {
            System.err.println("[MainServer] Erro fatal ao arrancar: " + e.getMessage());
            e.printStackTrace();
        }
    }
}