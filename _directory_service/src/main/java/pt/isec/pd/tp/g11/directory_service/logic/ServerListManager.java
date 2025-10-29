/*
 * Ficheiro: ServerListManager.java
 * Objetivo: Gere a lista de servidores ativos ("cérebro" da diretoria).
 * Responsabilidade: Adicionar/atualizar servidores (via REGISTAR/HEARTBEAT),
 * remover servidores por timeout (17s), e fornecer o servidor
 * principal (o mais antigo) a pedido dos clientes.
 * Corre numa thread própria para verificar timeouts de forma assíncrona.
 */

package pt.isec.pd.tp.g11.directory_service.logic;

// Importa os teus ficheiros 'common'
import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ServerListManager extends Thread {

    // Timeout de 17 segundos, conforme o enunciado [cite: 150]
    public static final long SERVER_TIMEOUT_MS = 17000;

    // A lista de servidores. "LinkedList" é boa para
    // adicionar/remover rapidamente.
    // A lista está ordenada por ordem de registo [cite: 147]
    private final List<ServerInfo> serverList = new LinkedList<>();

    public ServerListManager() {
        // Define esta thread como "daemon" para que não impeça
        // o programa de fechar.
        setDaemon(true);
    }

    /**
     * Método principal da thread. Verifica timeouts a cada 5 segundos.
     */
    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(5000); // Verificar a cada 5 segundos
                checkTimeouts();
            } catch (InterruptedException e) {
                System.out.println("Thread de timeout interrompida.");
            }
        }
    }

    /**
     * Processa um objeto UDPMessage recebido pelo DirectoryService.
     * Este método é SÍNCRONO e chamado pela thread do DirectoryService.
     *
     * @return O objeto UDPMessage de resposta a ser enviado ao remetente.
     */
    public synchronized UDPMessage processRequest(UDPMessage message, InetAddress senderAddress) {
        // ... (código igual)

        MessageType command = message.getType();
        String[] payload = message.getPayload();

        switch (command) {
            case SERVER_REGISTER: // APENAS o registo inicial do MainServer [cite: 87, 88]
                if (payload == null || payload.length != 2) {
                    return new UDPMessage(MessageType.ERROR, "INVALID_REGISTER_FORMAT");
                }
                try {
                    int clientPort = Integer.parseInt(payload[0]);
                    int dbPort = Integer.parseInt(payload[1]);
                    handleRegister(senderAddress, clientPort, dbPort);

                    // Responde com o servidor principal atual
                    return getPrimaryServerDbInfo();
                } catch (NumberFormatException e) {
                    return new UDPMessage(MessageType.ERROR, "INVALID_PORT_NUMBER");
                }

            case SERVER_HEARTBEAT: // O heartbeat da HeartbeatService [cite: 126]
                if (payload == null || payload.length != 3) { // <-- Alterar para 3
                    return new UDPMessage(MessageType.ERROR, "INVALID_HEARTBEAT_FORMAT");
                }
                try {
                    int clientPort = Integer.parseInt(payload[0]);
                    int dbPort = Integer.parseInt(payload[1]);
                    int dbVersion = Integer.parseInt(payload[2]); // Podes usar este 'int' no futuro

                    // O handleRegister faz o "touch" ou adiciona
                    handleRegister(senderAddress, clientPort, dbPort);

                    // Responde com o servidor principal atual
                    return getPrimaryServerDbInfo();
                } catch (NumberFormatException e) {
                    return new UDPMessage(MessageType.ERROR, "INVALID_PORT_NUMBER");
                }

            case CLIENT_REQUEST_SERVER: // Pedido do Cliente [cite: 149]
                return getPrimaryServerClientInfo();

            // --- NOVOS CASOS (EM COMENTÁRIO) ---

            /*
            [cite_start]// [cite: 145]
            // Caso 4: Servidor a desligar-se de forma ordenada
            // O servidor envia isto (sem payload) antes de fechar.
            case SERVER_UNREGISTER:
                System.out.println("Pedido de anulação de registo de: " + senderAddress.getHostAddress());
                // A ação é remover o servidor da lista
                handleUnregister(senderAddress);
                // Não precisa de resposta obrigatória, mas podemos enviar um OK
                return new UDPMessage(MessageType.SERVER_REGISTER_OK); // Reutilizar o OK
            */

            /*
            [cite_start]// [cite: 114]
            // (Avançado) Caso 5: Servidor backup falhou a Sincronização da BD
            // O servidor envia isto (sem payload) antes de terminar
            case SERVER_SYNC_FAILED:
                 System.out.println("Servidor backup falhou sync e vai sair: " + senderAddress.getHostAddress());
                 // A ação é a mesma que o unregister
                 handleUnregister(senderAddress);
                 // Também não precisa de resposta, mas podemos ser simpáticos
                 return new UDPMessage(MessageType.SERVER_REGISTER_OK);
            */

            // --- FIM DOS NOVOS CASOS ---

            default:
                return new UDPMessage(MessageType.ERROR, "UNKNOWN_COMMAND");
        }
    }

    /**
     * Regista um novo servidor ou atualiza o seu heartbeat se já existir.
     */
    private void handleRegister(InetAddress address, int clientPort, int dbPort) {
        // O acesso à lista é sincronizado pelo 'synchronized' no processRequest

        // Tenta encontrar o servidor na lista
        for (ServerInfo info : serverList) {
            if (info.getAddress().equals(address)) {
                // Encontrado. Apenas atualiza o heartbeat.
                info.touch();
                // NOTA: No futuro, podes querer atualizar os portos aqui também,
                // caso o servidor reinicie com portos diferentes.
                // ex: info.updatePorts(clientPort, dbPort);
                System.out.println("Heartbeat atualizado para: " + address.getHostAddress());
                return;
            }
        }

        // Não encontrado. Adiciona como um novo servidor no FIM da lista.
        ServerInfo newServer = new ServerInfo(address, clientPort, dbPort);
        serverList.add(newServer);
        System.out.println("Novo servidor registado: " + address.getHostAddress() + " (Total: " + serverList.size() + ")");
    }

    // --- NOVA FUNÇÃO DE HANDLER (EM COMENTÁRIO) ---
    /*
     * Remove um servidor da lista, com base no seu endereço.
     * (Usado por SERVER_UNREGISTER e SERVER_SYNC_FAILED)
     */
    /*
    private void handleUnregister(InetAddress address) {
        // O removeIf é uma forma limpa de iterar e remover
        serverList.removeIf(server -> server.getAddress().equals(address));
        System.out.println("Servidor " + address.getHostAddress() + " removido a pedido. (Total: " + serverList.size() + ")");
    }
    */
    // --- FIM DA NOVA FUNÇÃO ---


    /**
     * Se for um Cliente a pedir, envia-se o Porto de Cliente.
     * Retorna a informação do servidor principal (o primeiro da lista).
     */
    private UDPMessage getPrimaryServerClientInfo() {
        // O acesso à lista é sincronizado pelo 'synchronized' no processRequest

        if (serverList.isEmpty()) {
            return new UDPMessage(MessageType.ERROR, "NO_SERVERS_AVAILABLE");
        }

        // O principal é o primeiro da lista (o mais antigo) [cite: 149]
        ServerInfo primary = serverList.get(0);

        // Responde com o tipo CLIENT_RESPONSE_SERVER e o payload [IP, PORTO]
        return new UDPMessage(MessageType.CLIENT_RESPONSE_SERVER,
                primary.getAddress().getHostAddress(),
                String.valueOf(primary.getClientPort())
        );
    }


    /**
     * Se for um Servidor a pedir (seja REGISTER ou HEARTBEAT), envia-se o Porto de BD.
     */
    private UDPMessage getPrimaryServerDbInfo() {
        if (serverList.isEmpty()) {
            return new UDPMessage(MessageType.ERROR, "NO_SERVERS_AVAILABLE");
        }
        ServerInfo primary = serverList.get(0);
        // Envia o Porto de BD
        return new UDPMessage(MessageType.SERVER_REGISTER_OK, // Usar um tipo de msg diferente
                primary.getAddress().getHostAddress(),
                String.valueOf(primary.getDbPort())
        );
    }

    /**
     * Itera a lista e remove servidores que excederam o timeout.
     */
    private synchronized void checkTimeouts() {
        long now = System.currentTimeMillis();
        // Usamos um Iterador para poder remover da lista de forma segura
        // enquanto iteramos por ela.
        Iterator<ServerInfo> iterator = serverList.iterator();
        while (iterator.hasNext()) {
            ServerInfo server = iterator.next();

            // --- ESTA É A LINHA CORRIGIDA ---
            if (now - server.getLastHeartbeat() > SERVER_TIMEOUT_MS) {
                iterator.remove();
                System.out.println("Servidor " + server.getAddress().getHostAddress() + " removido por timeout. (Total: " + serverList.size() + ")");
            }
        }
    }
}