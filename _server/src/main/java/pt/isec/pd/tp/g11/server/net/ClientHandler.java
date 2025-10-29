/*
 * Ficheiro: ClientHandler.java
 * Objetivo: Thread responsável por tratar da comunicação com UM cliente TCP.
 * Responsabilidade: Ler pedidos (TCPMessage) do cliente, processá-los
 * (ex: verificar login, aceder à BD) e enviar respostas (TCPMessage).
 */
package pt.isec.pd.tp.g11.server.net;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.TCPMessage;
import pt.isec.pd.tp.g11.common.model.Docente; // Importar Doc// ente
import pt.isec.pd.tp.g11.common.model.User;
// import pt.isec.pd.tp.g11.server.db.DatabaseManager; // Vais precisar disto

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ClientHandler extends Thread {

    private final Socket clientSocket;
    // private final DatabaseManager dbManager; // O gestor da BD
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private User authenticatedUser = null;

    public ClientHandler(Socket socket /*, DatabaseManager dbManager */) {
        this.clientSocket = socket;
        // this.dbManager = dbManager;
        setName("ClientHandler-" + socket.getInetAddress());
    }

    @Override
    public void run() {
        try {
            // 1. Setup dos streams (Object Streams para TCPMessage)
            this.out = new ObjectOutputStream(clientSocket.getOutputStream());
            this.in = new ObjectInputStream(clientSocket.getInputStream());

            // 2. Aplicar o timeout de autenticação de 30s
            clientSocket.setSoTimeout(30000); // 30 segundos

            // 3. Esperar pela primeira mensagem (Login ou Registo)
            TCPMessage request = (TCPMessage) in.readObject();

            // 4. Processar o primeiro pedido
            if (request.getType() == MessageType.LOGIN_REQUEST) {
                handleLogin(request); // Tenta autenticar
            } else if (request.getType() == MessageType.REGISTER_ESTUDANTE) {
                // TODO: handleRegisterEstudante(request);
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Registo ainda não implementado."));
            } else if (request.getType() == MessageType.REGISTER_DOCENTE) {
                // TODO: handleRegisterDocente(request);
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Registo ainda não implementado."));
            } else {
                // Mensagem inicial inválida
                out.writeObject(new TCPMessage(MessageType.LOGIN_FAILED, "Protocolo inválido. Esperado LOGIN ou REGISTER."));
            }

            // 5. Se autenticação/registo falhou, a thread termina (socket já foi fechado pelo handler)
            if (authenticatedUser == null) {
                return; // Termina a thread ClientHandler
            }

            // 6. LOGIN/REGISTO COM SUCESSO: Remover o timeout e entrar no loop principal
            clientSocket.setSoTimeout(0); // 0 = timeout infinito para a sessão
            System.out.println("[ClientHandler] Utilizador " + authenticatedUser.getEmail() + " autenticado.");

            // 7. Loop principal: Espera por mais pedidos do cliente autenticado
            while (!clientSocket.isClosed()) {
                TCPMessage mainRequest = (TCPMessage) in.readObject();
                // TODO: handleAuthenticatedRequest(mainRequest);
                // ex: if (mainRequest.getType() == MessageType.CREATE_QUESTION) { ... }
                System.out.println("[ClientHandler] Recebido pedido: " + mainRequest.getType()); // Placeholder
            }

        } catch (SocketTimeoutException e) {
            System.out.println("[ClientHandler] Cliente " + clientSocket.getInetAddress() + " não se autenticou a tempo (30s).");
        } catch (java.io.EOFException e) {
            System.out.println("[ClientHandler] Cliente " + clientSocket.getInetAddress() + " desligou-se abruptamente.");
        } catch (Exception e) {
            System.out.println("[ClientHandler] Erro na comunicação com " + clientSocket.getInetAddress() + ": " + e.getMessage());
        } finally {
            // Garante que o socket é sempre fechado quando a thread termina
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (Exception e) { /* ignorar */ }
            System.out.println("[ClientHandler] Ligação com " + clientSocket.getInetAddress() + " terminada.");
        }
    }

    /**
     * Tenta autenticar o utilizador com base nas credenciais recebidas.
     * Atualiza 'authenticatedUser' e envia a resposta ao cliente.
     */
    private void handleLogin(TCPMessage request) throws Exception {
        if (!(request.getPayload() instanceof String[])) {
            out.writeObject(new TCPMessage(MessageType.LOGIN_FAILED, "Payload inválido para LOGIN_REQUEST."));
            return;
        }

        String[] credentials = (String[]) request.getPayload(); // {email, pass}
        if (credentials.length != 2) {
            out.writeObject(new TCPMessage(MessageType.LOGIN_FAILED, "Payload inválido para LOGIN_REQUEST (esperado {email, pass})."));
            return;
        }

        // TODO: Fazer a lógica real da Base de Dados
        // User user = dbManager.checkLogin(credentials[0], credentials[1]);

        // --- Exemplo Falso (para testar) ---
        User user = null;
        if (credentials[0].equals("docente@isec.pt") && credentials[1].equals("1234")) {
            user = new Docente(1, "Docente Teste", "docente@isec.pt"); // Usar a classe Docente
        }
        // --- Fim do Exemplo Falso ---

        if (user != null) {
            this.authenticatedUser = user; // Guarda o utilizador autenticado
            out.writeObject(new TCPMessage(MessageType.LOGIN_SUCCESS, user)); // Envia o objeto User
        } else {
            out.writeObject(new TCPMessage(MessageType.LOGIN_FAILED, "Credenciais inválidas."));
            // Não fecha o socket aqui, deixa o 'run()' tratar disso
        }
    }

    // TODO: Implementar handleRegisterEstudante e handleRegisterDocente
    // TODO: Implementar handleAuthenticatedRequest (o switch principal para utilizadores logados)
}