package pt.isec.pd.tp.g11.common.messages;

import java.io.Serializable;
import java.net.InetAddress;

public class ServerInfoMessage implements Serializable {

    private static final long serialVersionUID = 2L;
    InetAddress ip;
    int clientsPort, dbSyncPort;

    public ServerInfoMessage(InetAddress ip, int clientsPort, int dbSyncPort) {
        this.ip = ip;
        this.clientsPort = clientsPort;
        this.dbSyncPort = dbSyncPort;
    }

    public InetAddress getIp() {return ip;}
    public int getClientsPort() {return clientsPort;}
    public int getDbSyncPort() {return dbSyncPort;}

    @Override
    public String toString()
    {
        return "ServerInfo{" +
                "ip=" + ip.getHostAddress() +
                ", portClientes=" + clientsPort +
                ", portDbSync=" + dbSyncPort +
                '}';
    }
}
