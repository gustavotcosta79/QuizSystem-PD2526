/*
 * Ficheiro: ServerConfig.java
 * Objetivo: Classe de ajuda para ler e armazenar os argumentos
 * da linha de comando do Servidor, conforme o enunciado.
 */
package pt.isec.pd.tp.g11.server;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerConfig {

    private InetAddress directoryAddress;
    private int directoryPort;
    private String dbPath;
    private InetAddress multicastAddress;

    public ServerConfig(String[] args) throws UnknownHostException, NumberFormatException {
        // O enunciado pede 3 argumentos:
        // 1. Endereço IP e porto UDP do serviço de diretoria (ex: "127.0.0.1:5000")
        // 2. Caminho da diretoria de armazenamento da BD (ex: "db_files/")
        // 3. Endereço IP da interface de rede local para multicast (ex: "230.30.30.30")

        if (args.length != 3) {
            throw new IllegalArgumentException("Erro: São necessários 3 argumentos.\n" +
                    "Uso: java MainServer <dir_ip:dir_port> <db_path> <multicast_ip>");
        }

        // Argumento 1: Diretoria
        String[] dirInfo = args[0].split(":");
        if (dirInfo.length != 2) throw new IllegalArgumentException("Formato inválido para <dir_ip:dir_port>");
        this.directoryAddress = InetAddress.getByName(dirInfo[0]);
        this.directoryPort = Integer.parseInt(dirInfo[1]);

        // Argumento 2: Base de Dados
        this.dbPath = args[1];

        // Argumento 3: Multicast
        this.multicastAddress = InetAddress.getByName(args[2]);
    }

    // Getters
    public InetAddress getDirectoryAddress() { return directoryAddress; }
    public int getDirectoryPort() { return directoryPort; }
    public String getDbPath() { return dbPath; }
    public InetAddress getMulticastAddress() { return multicastAddress; }
}