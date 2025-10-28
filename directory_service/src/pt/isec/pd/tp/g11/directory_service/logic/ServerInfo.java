/*
 * Ficheiro: ServerInfo.java
 * Objetivo: Classe "modelo" para armazenar a informação
 * de um Servidor ativo.
 * Responsabilidade: Guardar o IP, os portos (clientes e BD)
 * e o carimbo de tempo do último heartbeat recebido.
 */
package pt.isec.pd.tp.g11.directory_service.logic;

import java.net.InetAddress;

public class ServerInfo {
    private final InetAddress address;
    private final int clientPort; // Porto TCP para Clientes
    private final int dbPort;     // Porto TCP para BD Sync
    private long lastHeartbeat;   // Timestamp do último heartbeat

    public ServerInfo(InetAddress address, int clientPort, int dbPort) {
        this.address = address;
        this.clientPort = clientPort;
        this.dbPort = dbPort;
        this.touch(); // Define o timestamp inicial
    }

    /**
     * Atualiza o timestamp do último heartbeat para o tempo atual.
     */
    public void touch() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    // Getters
    public InetAddress getAddress() {
        return address;
    }

    public int getClientPort() {
        return clientPort;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    // Usado para verificar se um servidor que envia heartbeat já está na lista
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ServerInfo that = (ServerInfo) obj;
        // Um servidor é "igual" se tiver o mesmo IP
        // (Simplificação; podia tbm comparar portos)
        return address.equals(that.address);
    }
}