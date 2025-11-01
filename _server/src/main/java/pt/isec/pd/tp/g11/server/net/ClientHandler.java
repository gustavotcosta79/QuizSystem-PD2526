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
    import pt.isec.pd.tp.g11.common.model.Estudante;
    import pt.isec.pd.tp.g11.common.model.Question;
    import pt.isec.pd.tp.g11.common.model.User;
    import pt.isec.pd.tp.g11.server.db.DatabaseManager;
    import pt.isec.pd.tp.g11.server.db.DatabaseManager; // Vais precisar disto
    import pt.isec.pd.tp.g11.server.utils.SecurityUtils;

    import java.io.ObjectInputStream;
    import java.io.ObjectOutputStream;
    import java.net.Socket;
    import java.net.SocketTimeoutException;

    public class ClientHandler extends Thread {

        private final Socket clientSocket;
        private final DatabaseManager dbManager; // O gestor da BD
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private User authenticatedUser = null;

        public ClientHandler(Socket socket, DatabaseManager dbManager ) {
            this.clientSocket = socket;
            this.dbManager = dbManager;
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
                    handleLogin(request);
                } else if (request.getType() == MessageType.REGISTER_ESTUDANTE) {
                    // Descomentar e implementar
                    handleRegisterEstudante(request);
                } else if (request.getType() == MessageType.REGISTER_DOCENTE) {
                    handleRegisterDocente(request);
                } else {
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
                    // Tratar dos pedidos do utilizador logado
                    switch (mainRequest.getType()) {
                        case CREATE_QUESTION_REQUEST:
                            handleCreateQuestion(mainRequest);
                            break;

                        // TODO: Adicionar outros cases (LIST_QUESTIONS, EDIT_QUESTION, etc.)

                        default:
                            System.out.println("[ClientHandler] Recebido pedido desconhecido: " + mainRequest.getType());
                    }
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
                //User user = null;
                //if (credentials[0].equals("docente@isec.pt") && credentials[1].equals("1234")) {
                //    user = new Docente(1, "Docente Teste", "docente@isec.pt"); // Usar a classe Docente
                //}
            // --- Fim do Exemplo Falso ---

            // --- ALTERAÇÃO PRINCIPAL: USAR O DBMANAGER ---
            System.out.println("[ClientHandler] A verificar login para: " + credentials[0]);
            User user = dbManager.checkLogin(credentials[0], credentials[1]); // <-- A chamada real!
            // --- FIM DA ALTERAÇÃO ---


            if (user != null) {
                this.authenticatedUser = user; // Guarda o utilizador autenticado
                out.writeObject(new TCPMessage(MessageType.LOGIN_SUCCESS, user)); // Envia o objeto User
            } else {
                out.writeObject(new TCPMessage(MessageType.LOGIN_FAILED, "Credenciais inválidas."));
                // Não fecha o socket aqui, deixa o 'run()' tratar disso
            }
        }


        /**
         * Trata de um pedido de registo de um novo estudante.
         */
        private void handleRegisterEstudante(TCPMessage request) throws Exception {
            // O payload esperado é: Object[] { Estudante, String password }
            if (!(request.getPayload() instanceof Object[])) {
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Payload inválido."));
                return;
            }

            Object[] payload = (Object[]) request.getPayload();

            if (payload.length != 2 || !(payload[0] instanceof Estudante) || !(payload[1] instanceof String)) {
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Payload mal formatado para Registo de Estudante."));
                return;
            }

            Estudante estudante = (Estudante) payload[0];
            String plainPassword = (String) payload[1];

            // 1. Fazer o Hash da password
            String passwordHash = SecurityUtils.hashPassword(plainPassword);

            // 2. Tentar registar na Base de Dados
            if (dbManager.registerEstudante(estudante, passwordHash)) {
                // 3. Enviar SUCESSO
                out.writeObject(new TCPMessage(MessageType.REGISTER_SUCCESS));
                System.out.println("[ClientHandler] Novo estudante registado: " + estudante.getEmail());
                // Nota: Não fazemos login automático, o utilizador terá de fazer login
            } else {
                // 4. Enviar FALHA (Email ou Número já existem)
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Email ou Número de Estudante já existem."));
            }

            // Se o registo falhar ou for bem-sucedido, o 'authenticatedUser' continua 'null'.
            // O 'run()' vai ver que 'authenticatedUser == null' e vai fechar a ligação,
            // o que está correto para um pedido de registo que não faz login automático.
        }

        /**
         * Trata de um pedido de registo de um novo docente.
         */
        private void handleRegisterDocente(TCPMessage request) throws Exception {
            // O payload esperado é: Object[] { Docente, String password, String codigoRegisto }
            if (!(request.getPayload() instanceof Object[])) {
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Payload inválido."));
                return;
            }

            Object[] payload = (Object[]) request.getPayload();

            if (payload.length != 3 || !(payload[0] instanceof Docente) || !(payload[1] instanceof String) || !(payload[2] instanceof String)) {
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Payload mal formatado para Registo de Docente."));
                return;
            }

            Docente docente = (Docente) payload[0];
            String plainPassword = (String) payload[1];
            String registerCode = (String) payload[2];

            // 1. Fazer o Hash da password
            String passwordHash = SecurityUtils.hashPassword(plainPassword);

            // 2. Tentar registar na Base de Dados
            int resultCode = dbManager.registerDocente(docente, passwordHash, registerCode);

            // 3. Enviar resposta
            if (resultCode == 0) { // Sucesso
                out.writeObject(new TCPMessage(MessageType.REGISTER_SUCCESS));
                System.out.println("[ClientHandler] Novo docente registado: " + docente.getEmail());
            } else if (resultCode == 1) { // Código errado
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Código de registo de docente incorreto."));
            } else { // Erro 2 (Email duplicado ou outro)
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Email já existente ou erro interno."));
            }

            // Tal como no registo de estudante, o 'authenticatedUser' fica 'null'
            // e a thread termina, fechando o socket.
        }

        /**
         * Trata de um pedido de criação de uma nova pergunta.
         * Verifica se o utilizador é um Docente.
         */
        private void handleCreateQuestion(TCPMessage request) throws Exception {
            // 1. Verificar se o utilizador é um Docente
            //
            if (!(authenticatedUser instanceof Docente)) {
                out.writeObject(new TCPMessage(MessageType.CREATE_QUESTION_FAILED, "Apenas Docentes podem criar perguntas."));
                return;
            }

            // 2. Verificar o payload
            if (!(request.getPayload() instanceof Question)) {
                out.writeObject(new TCPMessage(MessageType.CREATE_QUESTION_FAILED, "Payload inválido para CREATE_QUESTION_REQUEST."));
                return;
            }

            Question question = (Question) request.getPayload();

            // 3. Chamar o DatabaseManager para inserir na BD
            // (O ID do docente é o do utilizador autenticado)
            String accessCode = dbManager.createQuestion(question, authenticatedUser.getId());

            // 4. Enviar a resposta
            if (accessCode != null) {
                System.out.println("[ClientHandler] Docente " + authenticatedUser.getEmail() + " criou pergunta. Código: " + accessCode);
                out.writeObject(new TCPMessage(MessageType.CREATE_QUESTION_SUCCESS, accessCode));
            } else {
                System.err.println("[ClientHandler] Falha ao criar pergunta na BD.");
                out.writeObject(new TCPMessage(MessageType.CREATE_QUESTION_FAILED, "Erro interno do servidor ao guardar a pergunta."));
            }
        }


        // TODO: Implementar  e handleRegisterDocente
        // TODO: Implementar handleAuthenticatedRequest (o switch principal para utilizadores logados)
    }