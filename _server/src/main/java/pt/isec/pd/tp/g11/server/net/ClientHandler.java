    /*
     * Ficheiro: ClientHandler.java
     * Objetivo: Thread responsável por tratar da comunicação com UM cliente TCP.
     * Responsabilidade: Ler pedidos (TCPMessage) do cliente, processá-los
     * (ex: verificar login, aceder à BD) e enviar respostas (TCPMessage).
     */
    package pt.isec.pd.tp.g11.server.net;

    import pt.isec.pd.tp.g11.common.enums.MessageType;
    import pt.isec.pd.tp.g11.common.messages.TCPMessage;
    import pt.isec.pd.tp.g11.common.model.*;
    import pt.isec.pd.tp.g11.server.db.DatabaseManager;
    import pt.isec.pd.tp.g11.server.utils.SecurityUtils;

    import java.io.ObjectInputStream;
    import java.io.ObjectOutputStream;
    import java.net.Socket;
    import java.net.SocketTimeoutException;
    import java.util.List;

    public class ClientHandler extends Thread {

        private final Socket clientSocket;
        private final DatabaseManager dbManager; // O gestor da BD
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private User authenticatedUser = null;
        private final HeartbeatService heartbeatService;
        private final List<ClientHandler> activeClients;

        public ClientHandler(Socket socket, DatabaseManager dbManager,
                             HeartbeatService heartbeatService, List<ClientHandler> activeClients) {
            this.clientSocket = socket;
            this.dbManager = dbManager;
            this.heartbeatService = heartbeatService;
            setName("ClientHandler-" + socket.getInetAddress());
            this.activeClients = activeClients;
        }

        @Override
        public void run() {
            // REGISTAR ESTE CLIENTE NA LISTA
            activeClients.add(this);
            try {
                //  Setup dos streams (Object Streams para TCPMessage)
                this.out = new ObjectOutputStream(clientSocket.getOutputStream());
                this.in = new ObjectInputStream(clientSocket.getInputStream());

                // timeout de autenticação de 30s
                clientSocket.setSoTimeout(30000); // 30 segundos

                // esperar pela primeira mensagem (Login ou Registo)
                TCPMessage request = (TCPMessage) in.readObject();

                // Processar o primeiro pedido
                if (request.getType() == MessageType.LOGIN_REQUEST) {
                    handleLogin(request);
                } else if (request.getType() == MessageType.REGISTER_ESTUDANTE) {
                    handleRegisterEstudante(request);
                } else if (request.getType() == MessageType.REGISTER_DOCENTE) {
                    handleRegisterDocente(request);
                } else {
                    out.writeObject(new TCPMessage(MessageType.LOGIN_FAILED, "Protocolo inválido. Esperado LOGIN ou REGISTER."));
                }

                // autenticação/registo falhou, a thread termina (socket já foi fechado pelo handler)
                if (authenticatedUser == null) {
                    return; // Termina a thread ClientHandler
                }

                // LOGIN/REGISTO COM SUCESSO: Remover o timeout e entrar no loop principal
                clientSocket.setSoTimeout(0); // 0 = timeout infinito para a sessão
                System.out.println("[ClientHandler] Utilizador " + authenticatedUser.getEmail() + " autenticado.");

                // Loop principal: Espera por mais pedidos do cliente autenticado
                while (!clientSocket.isClosed()) {
                    TCPMessage mainRequest = (TCPMessage) in.readObject();
                    // Tratar dos pedidos do utilizador logado
                    switch (mainRequest.getType()) {
                        case CREATE_QUESTION_REQUEST:
                            handleCreateQuestion(mainRequest);
                            break;
                        case GET_QUESTION_BY_CODE:
                            handleGetQuestionByCode(mainRequest);
                            break;

                        case SUBMIT_ANSWER:
                            handleSubmitAnswer(mainRequest);
                            break;

                        case GET_MY_QUESTIONS_REQUEST:
                            handleGetMyQuestions(mainRequest);
                            break;

                        case DELETE_QUESTION_REQUEST:
                            handleDeleteQuestion(mainRequest);
                            break;

                        case EDIT_QUESTION_REQUEST:
                            handleEditQuestion(mainRequest);
                            break;

                        case EDIT_PROFILE_DOCENTE_REQUEST:
                            handleEditProfileDocente(mainRequest);
                            break;

                        case EDIT_PROFILE_ESTUDANTE_REQUEST:
                            handleEditProfileEstudante(mainRequest);
                            break;

                        case GET_MY_ANSWERS_REQUEST:
                            handleGetMyAnswers(mainRequest);
                            break;

                        case GET_QUESTION_RESULTS_REQUEST:
                            handleGetQuestionResults(mainRequest);
                            break;

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
                    // REMOVER DA LISTA QUANDO SAIR
                    activeClients.remove(this);
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

            System.out.println("[ClientHandler] A verificar login para: " + credentials[0]);
            User user = dbManager.checkLogin(credentials[0], credentials[1]); // <-- A chamada real!

            if (user != null) {
                this.authenticatedUser = user; // Guarda o utilizador autenticado
                out.writeObject(new TCPMessage(MessageType.LOGIN_SUCCESS, user)); // Envia o objeto User
            } else {
                out.writeObject(new TCPMessage(MessageType.LOGIN_FAILED, "Credenciais inválidas."));
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

            // Fazer o Hash da password
            String passwordHash = SecurityUtils.hashPassword(plainPassword);

            // USAR O NOVO MÉTODO QUE DEVOLVE SQL
            String sqlQuery = dbManager.registerEstudante(estudante, passwordHash);

            if (sqlQuery != null) {
                // Sucesso na BD Local
                int newVersion = dbManager.getDbVersion();

                // Enviar Multicast
                heartbeatService.sendUpdate(sqlQuery, newVersion);

                out.writeObject(new TCPMessage(MessageType.REGISTER_SUCCESS));
                System.out.println("[ClientHandler] Estudante registado e propagado.");
            } else {
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Erro BD."));
            }
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

            // Fazer o Hash da password
            String passwordHash = SecurityUtils.hashPassword(plainPassword);

            // Tentar registar na Base de Dados
            String result = dbManager.registerDocente(docente, passwordHash, registerCode);

            // Enviar resposta
            if (result == null) {
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Email já existente ou erro interno."));
            } else if (result.equals("WRONG_CODE")) {
                // Caso "WRONG_CODE": O código de docente estava errado
                out.writeObject(new TCPMessage(MessageType.REGISTER_FAILED, "Código de registo de docente incorreto."));
            } else {
                // Caso SUCESSO (é uma query SQL):
                // Obter a nova versão
                int newVersion = dbManager.getDbVersion();

                // Enviar Multicast para os backups
                heartbeatService.sendUpdate(result, newVersion);

                // Responder ao Cliente
                out.writeObject(new TCPMessage(MessageType.REGISTER_SUCCESS));
                System.out.println("[ClientHandler] Docente registado e propagado para o cluster.");
            }
        }

        /**
         * Trata de um pedido de criação de uma nova pergunta.
         * Verifica se o utilizador é um Docente e notifica todos os clientes.
         */
        private void handleCreateQuestion(TCPMessage request) throws Exception {
            // Verificar se o utilizador é um Docente
            if (!(authenticatedUser instanceof Docente)) {
                out.writeObject(new TCPMessage(MessageType.CREATE_QUESTION_FAILED, "Apenas Docentes podem criar perguntas."));
                return;
            }

            // Verificar o payload
            if (!(request.getPayload() instanceof Question)) {
                out.writeObject(new TCPMessage(MessageType.CREATE_QUESTION_FAILED, "Payload inválido para CREATE_QUESTION_REQUEST."));
                return;
            }

            Question question = (Question) request.getPayload();

            // Chamar o DatabaseManager para inserir na BD
            // (O ID do docente é o do utilizador autenticado)
            String[] result = dbManager.createQuestion(question, authenticatedUser.getId());

            // Processar o resultado
            if (result != null) {
                String accessCode = result[0];
                String sqlQuery = result[1]; // SQL para os backups

                // Enviar Multicast (Sincronização da BD com Backups)
                int newVersion = dbManager.getDbVersion();
                heartbeatService.sendUpdate(sqlQuery, newVersion);

                // Responder ao Cliente que fez o pedido (Confirmação Síncrona)
                out.writeObject(new TCPMessage(MessageType.CREATE_QUESTION_SUCCESS, accessCode));

                // Notificar TODOS os clientes ligados (Notificação Assíncrona)
                String notificationText = "NOVA PERGUNTA Criada! O docente " + authenticatedUser.getNome() +
                        " criou um quiz com o código: \"" + result[0] + "\"";

                broadcast(notificationText);

                System.out.println("[ClientHandler] Pergunta criada, propagada aos backups e notificada aos clientes.");
            } else {
                System.err.println("[ClientHandler] Falha ao criar pergunta na BD.");
                out.writeObject(new TCPMessage(MessageType.CREATE_QUESTION_FAILED, "Erro interno do servidor ao guardar a pergunta."));
            }
        }

        /**
         * Processa a submissão de uma resposta de um estudante.
         */
        private void handleSubmitAnswer(TCPMessage request) throws Exception {
            // Payload esperado: AnswerPayload
            if (!(request.getPayload() instanceof AnswerPayload)) {
                out.writeObject(new TCPMessage(MessageType.SUBMIT_ANSWER_FAILED, "Payload inválido."));
                return;
            }

            AnswerPayload payload = (AnswerPayload) request.getPayload();

            int idPergunta = payload.getIdPergunta();
            String respostaLetra = payload.getRespostaLetra();

            // Usar o ID do estudante autenticado na sessão
            int idEstudante = authenticatedUser.getId();

            String sqlQuery = dbManager.submitAnswer(idEstudante, idPergunta, respostaLetra);

            if (sqlQuery != null) {
                // Enviar Multicast
                int newVersion = dbManager.getDbVersion();
                heartbeatService.sendUpdate(sqlQuery, newVersion);

                // Responder ao Cliente
                out.writeObject(new TCPMessage(MessageType.SUBMIT_ANSWER_SUCCESS));
                System.out.println("[ClientHandler] Resposta submetida e propagada.");
            } else {
                out.writeObject(new TCPMessage(MessageType.SUBMIT_ANSWER_FAILED, "Erro: Já respondeu ou erro de BD."));
            }

        }

        /**
         * Trata de um pedido de um estudante para obter uma pergunta
         * usando um código de acesso.
         */
        private void handleGetQuestionByCode(TCPMessage request) throws Exception {
            // Verificar se o utilizador é um Estudante
            if (!(authenticatedUser instanceof Estudante)) {
                out.writeObject(new TCPMessage(MessageType.GET_QUESTION_FAILED, "Apenas Estudantes podem responder a perguntas."));
                return;
            }

            // Verificar o payload (String accessCode)
            if (!(request.getPayload() instanceof String)) {
                out.writeObject(new TCPMessage(MessageType.GET_QUESTION_FAILED, "Payload inválido (esperado String)."));
                return;
            }

            String accessCode = (String) request.getPayload();

            Question question = dbManager.getActiveQuestionByCode(accessCode);

            // Enviar a resposta
            if (question != null) {
                // Sucesso! Envia o objeto Question completo
                out.writeObject(new TCPMessage(MessageType.GET_QUESTION_SUCCESS, question));
            } else {
                // Falha (código errado ou pergunta expirada/inexistente)
                out.writeObject(new TCPMessage(MessageType.GET_QUESTION_FAILED, "Código de acesso inválido ou a pergunta não está disponível."));
            }
        }

        /**
         * Trata de um pedido de um docente para listar as suas próprias perguntas.
         */
        private void handleGetMyQuestions(TCPMessage request) throws Exception {
            // Verificar se o utilizador é um Docente
            if (!(authenticatedUser instanceof Docente)) {
                out.writeObject(new TCPMessage(MessageType.GET_MY_QUESTIONS_FAILED, "Apenas Docentes podem listar perguntas."));
                return;
            }

            // Verificar o payload (String filtro)
            if (!(request.getPayload() instanceof String)) {
                out.writeObject(new TCPMessage(MessageType.GET_MY_QUESTIONS_FAILED, "Payload inválido (esperado String com o filtro)."));
                return;
            }
            String filter = (String) request.getPayload();

            // Chamar o DatabaseManager (usando o ID do utilizador autenticado)
            List<Question> questions = dbManager.getQuestionsByTeacher(authenticatedUser.getId(), filter);

            // Enviar a resposta (mesmo que a lista esteja vazia)
            // O cliente (ServerConnection) espera uma Lista, por isso enviamos uma lista (Serializable)
            // está à espera da resposta no métodogetMyQuestions (que estava bloqueado em in.readObject())
            out.writeObject(new TCPMessage(MessageType.GET_MY_QUESTIONS_SUCCESS, (java.io.Serializable) questions));
        }

        private void handleDeleteQuestion(TCPMessage request) throws Exception {
            // Validar utilizador e payload
            if (!(authenticatedUser instanceof Docente)) {
                out.writeObject(new TCPMessage(MessageType.DELETE_QUESTION_FAILED, "Apenas docentes."));
                return;
            }

            if (!(request.getPayload() instanceof String)) {
                out.writeObject(new TCPMessage(MessageType.DELETE_QUESTION_FAILED, "Payload inválido (esperado Código de Acesso)."));
                return;
            }

            String accessCode = (String) request.getPayload();
            int idDocente = authenticatedUser.getId();

            // Tentar eliminar
            String sqlQuery = dbManager.deleteQuestion(accessCode, idDocente);

            if (sqlQuery != null) {
                // Propagar para os Backups (Sincronização BD)
                int newVersion = dbManager.getDbVersion();
                heartbeatService.sendUpdate(sqlQuery, newVersion);

                // Responder ao Cliente (Sucesso Síncrono)
                out.writeObject(new TCPMessage(MessageType.DELETE_QUESTION_SUCCESS));

                // 5. NOTIFICAR TODOS (Broadcast Assíncrono)
                String msg = "ATENÇÃO: A pergunta com código " + accessCode + " foi CANCELADA pelo docente.";
                broadcast(msg);

                System.out.println("[ClientHandler] Pergunta " + accessCode + " eliminada, propagada e notificada.");
            } else {
                out.writeObject(new TCPMessage(MessageType.DELETE_QUESTION_FAILED, "Erro: Pergunta não existe, não é sua, ou já tem respostas."));
            }
        }

        private void handleEditQuestion(TCPMessage request) throws Exception {
            // Validar utilizador e payload
            if (!(authenticatedUser instanceof Docente)) {
                out.writeObject(new TCPMessage(MessageType.EDIT_QUESTION_FAILED, "Apenas docentes."));
                return;
            }
            // Payload esperado: Object[] { String accessCode, Question newQuestionData }
            if (!(request.getPayload() instanceof Object[]) || ((Object[]) request.getPayload()).length != 2) {
                out.writeObject(new TCPMessage(MessageType.EDIT_QUESTION_FAILED, "Payload inválido."));
                return;
            }

            Object[] payload = (Object[]) request.getPayload();
            String accessCode = (String) payload[0];
            Question newQuestionData = (Question) payload[1];
            int idDocente = authenticatedUser.getId();

            // Tentar editar (Chama o método real do DBManager)
            String sqlQuery = dbManager.editQuestion(accessCode, newQuestionData, idDocente);

            if (sqlQuery != null) {
                // Propagar para os Backups (Sincronização BD)
                int newVersion = dbManager.getDbVersion();
                heartbeatService.sendUpdate(sqlQuery, newVersion);

                // Responder ao Cliente (Sucesso Síncrono)
                out.writeObject(new TCPMessage(MessageType.EDIT_QUESTION_SUCCESS));

                // NOTIFICAR TODOS (Broadcast Assíncrono) -> O NOVO PASSO
                String msg = "ATUALIZAÇÃO: A pergunta " + accessCode + " (\"" + newQuestionData.getEnunciado() + "\") foi alterada pelo docente.";
                broadcast(msg);

                System.out.println("[ClientHandler] Pergunta " + accessCode + " editada, propagada e notificada.");
            } else {
                out.writeObject(new TCPMessage(MessageType.EDIT_QUESTION_FAILED, "Erro: Pergunta não existe, não é sua, ou já tem respostas."));
            }
        }
        /**
         * Trata de um pedido de um Docente para atualizar o seu perfil.
         */
        private void handleEditProfileDocente(TCPMessage request) throws Exception {
            // Validar o utilizador (só um Docente pode editar um Docente)
            if (!(authenticatedUser instanceof Docente)) {
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_DOCENTE_FAILED, "Acesso negado."));
                return;
            }

            // Validar o payload: Object[] { Docente, String newPassword }
            if (!(request.getPayload() instanceof Object[] payload) || payload.length != 2 ||
                    !(payload[0] instanceof Docente) || !(payload[1] instanceof String)) {
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_DOCENTE_FAILED, "Payload inválido."));
                return;
            }

            Docente updatedDocente = (Docente) payload[0];
            String newPassword = (String) payload[1];

            // VERIFICAÇÃO DE SEGURANÇA CRÍTICA:
            // O utilizador só pode editar o seu PRÓPRIO perfil.
            if (updatedDocente.getId() != authenticatedUser.getId()) {
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_DOCENTE_FAILED, "Não pode editar o perfil de outro utilizador."));
                return;
            }

            //  Fazer o Hash da nova password (se existir)
            String passwordHash = null;
            if (newPassword != null && !newPassword.isEmpty()) {
                passwordHash = SecurityUtils.hashPassword(newPassword);
            }

            String sqlQuery = dbManager.updateDocente(updatedDocente, passwordHash);

            // Processar o resultado e sincronizar
            if (sqlQuery != null) {
                // Enviar Multicast
                int newVersion = dbManager.getDbVersion();
                heartbeatService.sendUpdate(sqlQuery, newVersion);

                // Responder ao Cliente
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_DOCENTE_SUCCESS));

                // Atualizar o objeto de sessão local
                this.authenticatedUser = updatedDocente;
                System.out.println("[ClientHandler] Docente " + updatedDocente.getEmail() + " atualizou o perfil.");
            } else {
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_DOCENTE_FAILED, "Erro: Email pode já estar em uso."));
            }
        }

        /**
         * Trata de um pedido de um Estudante para atualizar o seu perfil.
         */
        private void handleEditProfileEstudante(TCPMessage request) throws Exception {
            // Validar o utilizador (só um Estudante pode editar um Estudante)
            if (!(authenticatedUser instanceof Estudante)) {
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_ESTUDANTE_FAILED, "Acesso negado."));
                return;
            }

            // Validar o payload: Object[] { Estudante, String newPassword }
            if (!(request.getPayload() instanceof Object[] payload) || payload.length != 2 ||
                    !(payload[0] instanceof Estudante) || !(payload[1] instanceof String)) {
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_ESTUDANTE_FAILED, "Payload inválido."));
                return;
            }

            Estudante updatedEstudante = (Estudante) payload[0];
            String newPassword = (String) payload[1];

            // VERIFICAÇÃO DE SEGURANÇA CRÍTICA:
            if (updatedEstudante.getId() != authenticatedUser.getId()) {
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_ESTUDANTE_FAILED, "Não pode editar o perfil de outro utilizador."));
                return;
            }

            // Fazer o Hash da nova password (se existir)
            String passwordHash = null;
            if (newPassword != null && !newPassword.isEmpty()) {
                passwordHash = SecurityUtils.hashPassword(newPassword);
            }

            String sqlQuery = dbManager.updateEstudante(updatedEstudante, passwordHash);

            if (sqlQuery != null) {
                // Enviar Multicast
                int newVersion = dbManager.getDbVersion();
                heartbeatService.sendUpdate(sqlQuery, newVersion);

                // Responder ao Cliente
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_ESTUDANTE_SUCCESS));

                // Atualizar o objeto de sessão local
                this.authenticatedUser = updatedEstudante;
                System.out.println("[ClientHandler] Estudante " + updatedEstudante.getEmail() + " atualizou o perfil.");
            } else {
                out.writeObject(new TCPMessage(MessageType.EDIT_PROFILE_ESTUDANTE_FAILED, "Erro: Email ou número de estudante podem já estar em uso."));
            }
        }

        /**
         * Trata de um pedido de um estudante para ver o seu histórico de respostas.
         */
        private void handleGetMyAnswers(TCPMessage request) throws Exception {
            //  Validar utilizador
            if (!(authenticatedUser instanceof Estudante)) {
                out.writeObject(new TCPMessage(MessageType.GET_MY_ANSWERS_FAILED, "Apenas estudantes."));
                return;
            }


            int idEstudante = authenticatedUser.getId();
            List<SubmittedAnswer> answers = dbManager.getSubmittedAnswers(idEstudante);

            // Enviar a resposta (é sempre sucesso, mesmo que a lista esteja vazia)
            // O List<SubmittedAnswer> é Serializable
            out.writeObject(new TCPMessage(MessageType.GET_MY_ANSWERS_SUCCESS, (java.io.Serializable) answers));
        }

        /**
         * Trata de um pedido de um docente para ver os resultados de uma pergunta expirada.
         */
        private void handleGetQuestionResults(TCPMessage request) throws Exception {
            if (!(authenticatedUser instanceof Docente)) {
                out.writeObject(new TCPMessage(MessageType.GET_QUESTION_RESULTS_FAILED, "Apenas docentes."));
                return;
            }

            String accessCode = (String) request.getPayload();
            int idDocente = authenticatedUser.getId();

            // Buscar a Pergunta (Detalhes para o cabeçalho do CSV)
            Question question = dbManager.getQuestionDetails(accessCode);

            // Validar se a pergunta existe e pertence ao docente
            if (question == null || question.getIdDocente() != idDocente) {
                out.writeObject(new TCPMessage(MessageType.GET_QUESTION_RESULTS_FAILED, "Pergunta não encontrada ou não lhe pertence."));
                return;
            }

            // Buscar a Lista de Resultados (Respostas dos alunos)
            List<QuestionResult> results = dbManager.getQuestionResults(accessCode, idDocente);

            //  enviar Object[] { Question, List<QuestionResult> }

            Object[] responsePayload = new Object[]{ question, results };

            out.writeObject(new TCPMessage(MessageType.GET_QUESTION_RESULTS_SUCCESS, responsePayload));
        }

        /**
         * Envia uma notificação assíncrona para ESTE cliente específico.
         */
        public void sendNotification(String message) {
            try {
                // Sincronizar porque o 'out' pode estar a ser usado noutro lado
                synchronized (out) {
                    out.writeObject(new TCPMessage(MessageType.NOTIFICATION, message));
                    out.flush();
                }
            } catch (Exception e) {
                System.err.println("Erro ao notificar cliente: " + e.getMessage());
            }
        }

        /**
         * Envia uma mensagem para TODOS os clientes ligados (Broadcast).
         */
        private void broadcast(String message) {
            for (ClientHandler client : activeClients) {
                client.sendNotification(message);
            }
        }

    }