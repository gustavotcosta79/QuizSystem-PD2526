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
        } catch (Exception e) { /* ignorar */ }
        System.out.println("[Comunicação] Ligação TCP fechada.");
    }

    /**
     * Envia um objeto Question (com as Options lá dentro) para o servidor.
     * Usa a ligação TCP estabelecida no login.
     * @param question O objeto Question preenchido pela ConsoleUI.
     * @return O código de acesso (String) se for bem-sucedido, ou null se falhar.
     */
    public String createQuestion(Question question) {
        if (tcpSocket == null || tcpSocket.isClosed() || out == null || in == null) {
            System.err.println("[Comunicação] Não está ligado ao servidor (faça login primeiro).");
            return null;
        }

        try {
            // 1. Criar e enviar o pedido
            System.out.println("[Comunicação] A enviar CREATE_QUESTION_REQUEST...");
            TCPMessage request = new TCPMessage(MessageType.CREATE_QUESTION_REQUEST, question);
            out.writeObject(request);
            out.flush();

            // 2. Esperar pela resposta do servidor
            TCPMessage response = (TCPMessage) in.readObject();

            // 3. Processar a resposta
            if (response.getType() == MessageType.CREATE_QUESTION_SUCCESS) {
                if (response.getPayload() instanceof String) {
                    return (String) response.getPayload(); // Retorna o Código de Acesso
                }
            } else {
                // Se falhou (CREATE_QUESTION_FAILED)
                String errorMsg = (response.getPayload() instanceof String) ? (String) response.getPayload() : "Erro desconhecido.";
                System.err.println("[Comunicação] Falha ao criar pergunta: " + errorMsg);
            }
        } catch (Exception e) {
            System.err.println("[Comunicação] Erro crítico ao criar pergunta: " + e.getMessage());
            // TODO: Tratar falha de ligação (pode ter de fechar e tentar reconectar)
        }
        return null; // Falha
    }




    /**
     * Submete a resposta de um estudante a uma pergunta.
     */
    public boolean submitAnswer(int idPergunta, String respostaLetra) {
        if (tcpSocket == null || !tcpSocket.isConnected()) return false;

        try {
            // --- ALTERAÇÃO AQUI ---
            // 1. Criar o objeto de payload
            AnswerPayload payload = new AnswerPayload(idPergunta, respostaLetra);
            TCPMessage request = new TCPMessage(MessageType.SUBMIT_ANSWER, payload);
            // --- FIM DA ALTERAÇÃO ---

            // 2. Enviar pedido
            out.writeObject(request);
            out.flush();

            // 3. Esperar resposta
            TCPMessage response = (TCPMessage) in.readObject();

            // ... (resto do método igual)
            return (response.getType() == MessageType.SUBMIT_ANSWER_SUCCESS);

        } catch (Exception e) {
            System.err.println("[Comunicação] Erro ao submeter resposta: " + e.getMessage());
            return false;
        }
    }

    /**
     * Pede ao servidor a lista de perguntas criadas pelo docente logado.
     * @param filter O filtro ("ALL", "ACTIVE", "FUTURE", "PAST")
     * @return Uma Lista de Questions, ou null se falhar.
     */
    public List<Question> getMyQuestions(String filter) {
        if (tcpSocket == null || tcpSocket.isClosed()) {
            System.err.println("[Comunicação] Não está ligado ao servidor.");
            return null;
        }

        try {
            // 1. Enviar pedido (para o ClientHandler que estava bloqueado no seu loop)
            TCPMessage request = new TCPMessage(MessageType.GET_MY_QUESTIONS_REQUEST, filter);
            out.writeObject(request);
            out.flush();

            // 2. Esperar resposta
            //recebe a resposta vinda do clientHandler
            TCPMessage response = (TCPMessage) in.readObject();

            // 3. Processar resposta
            if (response.getType() == MessageType.GET_MY_QUESTIONS_SUCCESS) {
                if (response.getPayload() instanceof List) {
                    // Fazemos um cast (é seguro se confiarmos no servidor)
                    return (List<Question>) response.getPayload(); // SUCESSO!
                }
            } else {
                String errorMsg = (response.getPayload() instanceof String) ? (String) response.getPayload() : "Erro desconhecido.";
                System.err.println("[Comunicação] Falha ao obter lista de perguntas: " + errorMsg);
            }
        } catch (Exception e) {
            System.err.println("[Comunicação] Erro crítico ao obter perguntas: " + e.getMessage());
        }
        return null; // Falha (devolve null, a UI trata disso)
    }

// ... (depois do teu método createQuestion)

    /**
     * Pede ao servidor uma pergunta, dado o seu código de acesso.
     *
     * @param accessCode O código de acesso (ex: "ABC123")
     * @return O objeto Question se for encontrado e ativo, ou null se falhar.
     */
    public Question getQuestionByCode(String accessCode) {
        if (tcpSocket == null || tcpSocket.isClosed()) {
            System.err.println("[Comunicação] Não está ligado ao servidor.");
            return null;
        }

        try {
            // 1. Enviar pedido
            TCPMessage request = new TCPMessage(MessageType.GET_QUESTION_BY_CODE, accessCode);
            out.writeObject(request);
            out.flush();

            // 2. Esperar resposta
            TCPMessage response = (TCPMessage) in.readObject();

            // 3. Processar resposta
            if (response.getType() == MessageType.GET_QUESTION_SUCCESS) {
                if (response.getPayload() instanceof Question) {
                    return (Question) response.getPayload(); // SUCESSO!
                }
            } else {
                String errorMsg = (response.getPayload() instanceof String) ? (String) response.getPayload() : "Erro desconhecido.";
                System.err.println("[Comunicação] Falha ao obter pergunta: " + errorMsg);
            }
        } catch (Exception e) {
            System.err.println("[Comunicação] Erro crítico ao obter pergunta: " + e.getMessage());
        }
        return null; // Falha
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