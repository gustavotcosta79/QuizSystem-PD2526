/*
 * Ficheiro: ServerConnection.java
 * [cite_start]Objetivo: Componente de Comunicação[cite: 173].
 * Responsabilidade: Gerir toda a comunicação de rede (UDP e TCP).
 * A Vista (ConsoleUI) chama métodos desta classe para
 * executar ações de rede.
 */
package pt.isec.pd.tp.g11.client.communication;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.TCPMessage;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.common.model.*;
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;


import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

public class ServerConnection {

    private static final int DIRECTORY_RESPONSE_TIMEOUT_MS = 3000;

    // Info da Diretoria (do arranque)
    private final InetAddress dirAddress;
    private final int dirPort;

    // Info do Servidor (descoberto via UDP)
    private String serverIp;
    private int serverPort;

    //guardar o ultimo email e password, para re-autenticar depois
    private String lastEmail = null;
    private String lastPassword = null;
    private String currentServerIp = null;
    private int currentServerPort = 0;

    // TODO: A ligação TCP principal e os ObjectStreams
    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ServerConnection(String dirInfo) throws Exception {
        String[] parts = dirInfo.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Formato inválido para <dir_ip:dir_port>");

        this.dirAddress = InetAddress.getByName(parts[0]);
        this.dirPort = Integer.parseInt(parts[1]);
    }

    /**
     * Contacta o Serviço de Diretoria (UDP) para descobrir
     * o IP/Porto do servidor principal.
     * @return true se bem-sucedido, false se afalhar.
     */
    public boolean findServer() {
        System.out.println("[Comunicação] A contactar diretoria em " + dirAddress.getHostAddress() + ":" + dirPort);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(DIRECTORY_RESPONSE_TIMEOUT_MS);

            // 1. Criar e enviar pedido
            UDPMessage requestMsg = new UDPMessage(MessageType.CLIENT_REQUEST_SERVER);
            byte[] sendData = SerializationUtils.serialize(requestMsg);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, dirAddress, dirPort);
            socket.send(sendPacket);

            // 2. Receber resposta
            byte[] receiveBuffer = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            // 3. Processar resposta
            UDPMessage responseMsg = (UDPMessage) SerializationUtils.deserialize(receivePacket.getData());

            if (responseMsg.getType() == MessageType.CLIENT_RESPONSE_SERVER) {
                String[] payload = responseMsg.getPayload();
                this.serverIp = payload[0];
                this.serverPort = Integer.parseInt(payload[1]); // Este é o clientPort do servidor
                System.out.println("[Comunicação] Servidor encontrado em: " + serverIp + ":" + serverPort);
                return true;
            } else {
                System.err.println("[Comunicação] Diretoria respondeu com erro: " + responseMsg.getPayload()[0]);
                return false;
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[Comunicação] Diretoria não respondeu (timeout).");
            return false;
        } catch (Exception e) {
            System.err.println("[Comunicação] Erro ao procurar servidor: " + e.getMessage());
            return false;
        }
    }

    public User login(String email, String password) {
        // Se já tivermos uma ligação, tentar reutilizá-la?
        // Por agora, vamos assumir que o login é a primeira ação TCP.
        if (tcpSocket != null && !tcpSocket.isClosed()) {
            System.err.println("[Comunicação] Já existe uma ligação TCP ativa.");
            // Poderíamos tentar fazer logout primeiro, ou simplesmente falhar.
            return null;
        }

        if (serverIp == null) {
            System.err.println("Erro: Servidor principal não encontrado via UDP.");
            return null;
        }

        try {
            // 1. Estabelecer a ligação TCP permanente (NÃO usar try-with-resources)
            System.out.println("[Comunicação] A ligar (TCP) a " + serverIp + ":" + serverPort + "...");
            this.tcpSocket = new Socket(serverIp, serverPort);
            // IMPORTANTE: Criar o Output stream PRIMEIRO
            this.out = new ObjectOutputStream(tcpSocket.getOutputStream());
            this.in = new ObjectInputStream(tcpSocket.getInputStream());
            System.out.println("[Comunicação] Ligação TCP estabelecida.");

            // 2. Criar e enviar a mensagem de Login
            String[] credentials = {email, password};
            TCPMessage loginRequest = new TCPMessage(MessageType.LOGIN_REQUEST, credentials);
            System.out.println("[Comunicação] A enviar pedido de LOGIN...");
            out.writeObject(loginRequest);
            out.flush(); // Garante que a mensagem é enviada

            // 3. Esperar pela resposta do servidor (LOGIN_SUCCESS ou LOGIN_FAILED)
            System.out.println("[Comunicação] A aguardar resposta do servidor...");
            Object responseObj = in.readObject(); // Bloqueia até receber resposta

            if (!(responseObj instanceof TCPMessage)) {
                System.err.println("[Comunicação] Resposta inválida do servidor.");
                closeConnection(); // Fechar ligação
                return null;
            }

            TCPMessage response = (TCPMessage) responseObj;

            // 4. Processar a resposta
            if (response.getType() == MessageType.LOGIN_SUCCESS) {
                if (response.getPayload() instanceof User) {
                    User user = (User) response.getPayload();
                    System.out.println("[Comunicação] Login bem-sucedido para: " + user.getEmail());

                    // GUARDAR CREDENCIAIS
                    this.lastEmail = email;
                    this.lastPassword = password;
                    this.currentServerIp = this.serverIp;     // Guardar onde estamos ligados
                    this.currentServerPort = this.serverPort; // Para comparar no failover
                    // TODO: Iniciar a thread de escuta de notificações assíncronas
                    // this.notificationListener = new NotificationListener(in);
                    // this.notificationListener.start();

                    return user;
                } else {
                    System.err.println("[Comunicação] Payload inválido para LOGIN_SUCCESS.");
                    closeConnection();
                    return null;
                }
            } else {
                // Login falhou (ex: LOGIN_FAILED ou outro erro)
                String errorMsg = (response.getPayload() instanceof String) ? (String) response.getPayload() : "Erro desconhecido.";
                System.err.println("[Comunicação] Login falhou: " + errorMsg);
                closeConnection(); // Fechar ligação em caso de falha
                return null;
            }

        } catch (Exception e) {
            System.err.println("[Comunicação] Erro durante o login: " + e.getMessage());
            closeConnection(); // Garante que fecha a ligação em caso de erro
            return null;
        }
    }



    /**
     * Tenta registar um novo estudante no servidor.
     * @param estudante O objeto Estudante com os dados (ID pode ser 0)
     * @param password A password em texto simples
     * @return true se o registo for bem-sucedido, false caso contrário
     */
    public boolean registerEstudante(Estudante estudante, String password) {
        if (serverIp == null) {
            System.err.println("Erro: Servidor principal não encontrado.");
            return false;
        }

        // O registo usa uma ligação TCP temporária.
        // Não interfere com a ligação principal (this.tcpSocket).
        try (Socket tempSocket = new Socket(serverIp, serverPort);
             ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream());
             ObjectInputStream tempIn = new ObjectInputStream(tempSocket.getInputStream()))
        {
            System.out.println("[Comunicação] A enviar pedido de REGISTAR_ESTUDANTE...");

            // 1. Preparar o payload: um array de Objeto
            // { Objeto Estudante, String password }
            Object[] payload = { estudante, password };
            TCPMessage registerRequest = new TCPMessage(MessageType.REGISTER_ESTUDANTE, payload);

            // 2. Enviar pedido
            tempOut.writeObject(registerRequest);
            tempOut.flush();

            // 3. Esperar pela resposta (REGISTER_SUCCESS ou REGISTER_FAILED)
            TCPMessage response = (TCPMessage) tempIn.readObject();

            // 4. Devolver true se a resposta for SUCESSO
            if (response.getType() == MessageType.REGISTER_SUCCESS) {
                System.out.println("[Comunicação] Registo bem-sucedido.");
                return true;
            } else {
                // Imprime a mensagem de erro vinda do servidor
                String errorMsg = (response.getPayload() instanceof String) ? (String) response.getPayload() : "Erro desconhecido.";
                System.err.println("[Comunicação] Registo falhou: " + errorMsg);
                return false;
            }

        } catch (Exception e) {
            System.err.println("[Comunicação] Erro crítico durante o registo: " + e.getMessage());
            return false;
        }
    }


    /**
     * Tenta registar um novo docente no servidor.
     * @param docente O objeto Docente com os dados (ID pode ser 0)
     * @param password A password em texto simples
     * @param codigoRegisto O código de registo de docente (texto simples)
     * @return 0 (Sucesso), 1 (Falha - Código errado), 2 (Falha - Email/Erro)
     */
    public int registerDocente(Docente docente, String password, String codigoRegisto) {
        if (serverIp == null) {
            System.err.println("Erro: Servidor principal não encontrado.");
            return 2;
        }

        // Registo usa uma ligação TCP temporária
        try (Socket tempSocket = new Socket(serverIp, serverPort);
             ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream());
             ObjectInputStream tempIn = new ObjectInputStream(tempSocket.getInputStream()))
        {
            System.out.println("[Comunicação] A enviar pedido de REGISTAR_DOCENTE...");

            // 1. Preparar o payload: um array de Objeto
            // { Objeto Docente, String password, String codigoRegisto }
            Object[] payload = { docente, password, codigoRegisto };
            TCPMessage registerRequest = new TCPMessage(MessageType.REGISTER_DOCENTE, payload);

            // 2. Enviar pedido
            tempOut.writeObject(registerRequest);
            tempOut.flush();

            // 3. Esperar pela resposta (REGISTER_SUCCESS ou REGISTER_FAILED)
            TCPMessage response = (TCPMessage) tempIn.readObject();

            // 4. Devolver resultado
            if (response.getType() == MessageType.REGISTER_SUCCESS) {
                System.out.println("[Comunicação] Registo bem-sucedido.");
                return 0; // Sucesso
            } else {
                // Imprime a mensagem de erro vinda do servidor
                String errorMsg = (response.getPayload() instanceof String) ? (String) response.getPayload() : "Erro desconhecido.";
                System.err.println("[Comunicação] Registo falhou: " + errorMsg);
                // Mapear a mensagem de erro para o código de retorno
                if (errorMsg.contains("Código")) {
                    return 1; // Código errado
                }
                return 2; // Email duplicado ou outro erro
            }

        } catch (Exception e) {
            System.err.println("[Comunicação] Erro crítico durante o registo: " + e.getMessage());
            return 2;
        }
    }



    /** Fecha a ligação TCP e os streams */
    public void closeConnection() {
        // TODO: Enviar mensagem de LOGOUT para o servidor antes de fechar?
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        } catch (Exception e) {     }
        finally {
            // ESSENCIAL PARA O FAILOVER
            out = null;
            in = null;
            tcpSocket = null;
        }
        System.out.println("[Comunicação] Ligação TCP fechada.");
    }

    /**
     * Envia um objeto Question (com as Options lá dentro) para o servidor.
     * Usa a ligação TCP estabelecida no login.
     * @param question O objeto Question preenchido pela ConsoleUI.
     * @return O código de acesso (String) se for bem-sucedido, ou null se falhar.
     */
    public String createQuestion(Question question) {
        if (serverIp == null) return null;

        TCPMessage request = new TCPMessage(MessageType.CREATE_QUESTION_REQUEST, question);
        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.CREATE_QUESTION_SUCCESS) {
            return (String) response.getPayload();
        }
        return null;
    }



    public boolean submitAnswer(int idPergunta, String respostaLetra) {
        // Validação básica de pré-conexão (opcional, pois o sendRequest também falha se for null)
        if (tcpSocket == null) return false;

        AnswerPayload payload = new AnswerPayload(idPergunta, respostaLetra);
        TCPMessage request = new TCPMessage(MessageType.SUBMIT_ANSWER, payload);

        TCPMessage response = sendRequest(request);

        return (response != null && response.getType() == MessageType.SUBMIT_ANSWER_SUCCESS);
    }

    public List<Question> getMyQuestions(String filter) {
        if (tcpSocket == null) return null;

        TCPMessage request = new TCPMessage(MessageType.GET_MY_QUESTIONS_REQUEST, filter);
        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.GET_MY_QUESTIONS_SUCCESS) {
            if (response.getPayload() instanceof List) {
                return (List<Question>) response.getPayload();
            }
        }
        // Se falhar ou payload inválido
        if (response != null) System.err.println("Erro ao obter perguntas: " + response.getPayload());
        return null;
    }

// ... (depois do teu método createQuestion)

    public Question getQuestionByCode(String accessCode) {
        if (tcpSocket == null) return null;

        TCPMessage request = new TCPMessage(MessageType.GET_QUESTION_BY_CODE, accessCode);
        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.GET_QUESTION_SUCCESS) {
            if (response.getPayload() instanceof Question) {
                return (Question) response.getPayload();
            }
        }
        return null;
    }

    public boolean deleteQuestion(String accessCode) {
        if (tcpSocket == null) return false;

        TCPMessage request = new TCPMessage(MessageType.DELETE_QUESTION_REQUEST, accessCode);
        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.DELETE_QUESTION_SUCCESS) {
            return true;
        }

        if (response != null) System.err.println("Falha ao eliminar: " + response.getPayload());
        return false;
    }

    // Em ServerConnection.java

    public boolean editQuestion(String accessCode, Question newQuestionData) {
        if (tcpSocket == null) return false;

        Object[] payload = { accessCode, newQuestionData };
        TCPMessage request = new TCPMessage(MessageType.EDIT_QUESTION_REQUEST, payload);

        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.EDIT_QUESTION_SUCCESS) {
            return true;
        }

        if (response != null) System.err.println("Falha ao editar: " + response.getPayload());
        return false;
    }


    public boolean updateProfileDocente(Docente docente, String newPassword) {
        if (tcpSocket == null) return false;

        Object[] payload = { docente, newPassword };
        TCPMessage request = new TCPMessage(MessageType.EDIT_PROFILE_DOCENTE_REQUEST, payload);

        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.EDIT_PROFILE_DOCENTE_SUCCESS) {
            return true;
        }

        if (response != null) System.err.println("Falha ao atualizar perfil: " + response.getPayload());
        return false;
    }

    public boolean updateProfileEstudante(Estudante estudante, String newPassword) {
        if (tcpSocket == null) return false;

        Object[] payload = { estudante, newPassword };
        TCPMessage request = new TCPMessage(MessageType.EDIT_PROFILE_ESTUDANTE_REQUEST, payload);

        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.EDIT_PROFILE_ESTUDANTE_SUCCESS) {
            return true;
        }

        if (response != null) System.err.println("Falha ao atualizar perfil: " + response.getPayload());
        return false;
    }

    public List<SubmittedAnswer> getMyAnswers() {
        if (tcpSocket == null) return null;

        TCPMessage request = new TCPMessage(MessageType.GET_MY_ANSWERS_REQUEST);
        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.GET_MY_ANSWERS_SUCCESS) {
            return (List<SubmittedAnswer>) response.getPayload();
        }
        return null;
    }

    // Adicione esta classe estática no final do ServerConnection ou num ficheiro separado
    public static class QuestionFullReport implements java.io.Serializable {
        public Question question;
        public List<QuestionResult> results;

        public QuestionFullReport(Question q, List<QuestionResult> r) {
            this.question = q;
            this.results = r;
        }
    }

    // Atualize o método getQuestionResults
    public QuestionFullReport getQuestionResults(String accessCode) {
        if (tcpSocket == null) return null;

        TCPMessage request = new TCPMessage(MessageType.GET_QUESTION_RESULTS_REQUEST, accessCode);
        TCPMessage response = sendRequest(request);

        if (response != null && response.getType() == MessageType.GET_QUESTION_RESULTS_SUCCESS) {
            // O Payload agora é Object[] { Question, List }
            if (response.getPayload() instanceof Object[]) {
                Object[] parts = (Object[]) response.getPayload();
                Question q = (Question) parts[0];
                List<QuestionResult> l = (List<QuestionResult>) parts[1];
                return new QuestionFullReport(q, l);
            }
        }
        return null;
    }


    /**
     * Tenta restabelecer a ligação ao cluster e re-autenticar o utilizador.
     * Implementa a lógica de espera de 20s exigida pelo enunciado.
     * @return true se a recuperação for bem-sucedida, caso contrário, termina a aplicação.
     */
    private boolean reconnectAndReauthenticate() {
        System.err.println("[Comunicação] Ligação perdida. A tentar recuperar...");

        String oldServerIp = this.currentServerIp;
        int oldServerPort = this.currentServerPort;

        closeConnection(); // Limpa a ligação antiga

        if (!findServer()) {
            System.err.println("[Failover] Falha na T1: Diretoria inacessível...");
            System.exit(1);
        }

        // --- LÓGICA CORRIGIDA ---

        // 1. Se for o mesmo servidor, espera 20s (conforme enunciado)
        if (this.serverIp.equals(oldServerIp) && this.serverPort == oldServerPort) {
            System.out.println("[Failover] Diretoria aponta para o mesmo servidor. A aguardar 20 segundos...");
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) { return false; }

            // 2. Tenta descobrir novamente após 20s
            if (!findServer()) {
                System.err.println("[Failover] Falha na T2: Não foi possível obter servidor...");
                System.exit(1);
            }

            // REMOVER O BLOCO QUE FAZIA SYSTEM.EXIT AQUI
            // Não queremos sair só porque o servidor é o mesmo. Queremos tentar o Login abaixo!
        }

        // 3. Atualiza os dados do servidor atual (seja novo ou o mesmo)
        this.currentServerIp = this.serverIp;
        this.currentServerPort = this.serverPort;

        // 4. Tenta Re-autenticar (Isto vai testar se o servidor está realmente vivo)
        if (lastEmail != null && lastPassword != null) {
            User user = login(this.lastEmail, this.lastPassword);
            if (user != null) {
                System.out.println("[Failover] Recuperação e re-autenticação BEM-SUCEDIDAS.");
                return true;
            }
        }

        System.err.println("[Failover] Falha definitiva (servidor recusou ligação ou login). A aplicação vai terminar.");
        System.exit(1);
        return false;
    }

    /**
     * Método genérico para enviar pedidos e tratar falhas de rede automaticamente.
     */
    private TCPMessage sendRequest(TCPMessage requestMsg) {
        try {
            // 1. Tenta enviar normal
            out.writeObject(requestMsg);
            out.flush();
            return (TCPMessage) in.readObject();

        } catch (Exception e) {
            System.err.println("[Comunicação] Erro no envio: " + e.getMessage() + ". A tentar Failover...");

            // 2. Se deu erro, tenta o Failover
            if (reconnectAndReauthenticate()) {
                try {
                    // 3. Se recuperou, reenvia o pedido original
                    System.out.println("[Comunicação] A reenviar pedido original...");
                    out.writeObject(requestMsg);
                    out.flush();
                    return (TCPMessage) in.readObject();
                } catch (Exception ex) {
                    System.err.println("[Comunicação] Falha ao reenviar após failover.");
                }
            }
        }
        return null; // Falha definitiva
    }
    // TODO: Métodos futuros que a Vista irá chamar
    /*
    public User login(String email, String password) {
        // 1. Estabelecer a ligação TCP permanente (tcpSocket)
        // 2. Enviar um TCPMessage de LOGIN
        // 3. Receber um TCPMessage de resposta
        // 4. Iniciar a thread de escuta de notificações (se o login for bom)
        // 5. Devolver o objeto User (ou null se falhar)
        return null;
    }
    */
}