/*
 * Ficheiro: ServerConnection.java
 * Atualizado para suportar Callbacks de GUI
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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ServerConnection {

    private static final int DIRECTORY_RESPONSE_TIMEOUT_MS = 3000;

    private final InetAddress dirAddress;
    private final int dirPort;

    private String serverIp;
    private int serverPort;

    private String lastEmail = null;
    private String lastPassword = null;
    private String currentServerIp = null;
    private int currentServerPort = 0;

    private Socket tcpSocket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private final BlockingQueue<TCPMessage> responseQueue = new LinkedBlockingQueue<>();
    private NotificationListener notificationListener;

    // --- NOVO: Callback para a GUI ---
    private Consumer<String> onNotification = null;

    public ServerConnection(String dirInfo) throws Exception {
        String[] parts = dirInfo.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("Formato inválido para <dir_ip:dir_port>");

        this.dirAddress = InetAddress.getByName(parts[0]);
        this.dirPort = Integer.parseInt(parts[1]);
    }

    // --- NOVO: Setter para a GUI registar o alerta ---
    public void setNotificationCallback(Consumer<String> callback) {
        this.onNotification = callback;
    }

    public boolean findServer() {
        System.out.println("[Comunicação] A contactar diretoria em " + dirAddress.getHostAddress() + ":" + dirPort);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(DIRECTORY_RESPONSE_TIMEOUT_MS);
            UDPMessage requestMsg = new UDPMessage(MessageType.CLIENT_REQUEST_SERVER);
            byte[] sendData = SerializationUtils.serialize(requestMsg);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, dirAddress, dirPort);
            socket.send(sendPacket);

            byte[] receiveBuffer = new byte[4096];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            UDPMessage responseMsg = (UDPMessage) SerializationUtils.deserialize(receivePacket.getData());

            if (responseMsg.getType() == MessageType.CLIENT_RESPONSE_SERVER) {
                String[] payload = responseMsg.getPayload();
                this.serverIp = payload[0];
                this.serverPort = Integer.parseInt(payload[1]);
                System.out.println("[Comunicação] Servidor encontrado em: " + serverIp + ":" + serverPort);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            System.err.println("[Comunicação] Erro ao procurar servidor: " + e.getMessage());
            return false;
        }
    }

    public User login(String email, String password) {
        if (tcpSocket != null && !tcpSocket.isClosed()) {
            return null;
        }
        if (serverIp == null) {
            return null;
        }

        try {
            this.tcpSocket = new Socket(serverIp, serverPort);
            this.out = new ObjectOutputStream(tcpSocket.getOutputStream());
            this.in = new ObjectInputStream(tcpSocket.getInputStream());

            notificationListener = new NotificationListener(in, responseQueue, onNotification);
            notificationListener.start();

            String[] credentials = {email, password};
            TCPMessage loginRequest = new TCPMessage(MessageType.LOGIN_REQUEST, credentials);

            out.writeObject(loginRequest);
            out.flush();

            TCPMessage response = responseQueue.poll(10, TimeUnit.SECONDS);

            if (response == null) {
                closeConnection();
                return null;
            }

            if (response.getType() == MessageType.LOGIN_SUCCESS) {
                if (response.getPayload() instanceof User) {
                    User user = (User) response.getPayload();
                    this.lastEmail = email;
                    this.lastPassword = password;
                    this.currentServerIp = this.serverIp;
                    this.currentServerPort = this.serverPort;
                    return user;
                }
            }
            closeConnection();
            return null;

        } catch (Exception e) {
            closeConnection();
            return null;
        }
    }

    public boolean registerEstudante(Estudante estudante, String password) {
        if (serverIp == null) return false;
        try (Socket tempSocket = new Socket(serverIp, serverPort);
             ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream());
             ObjectInputStream tempIn = new ObjectInputStream(tempSocket.getInputStream()))
        {
            Object[] payload = { estudante, password };
            TCPMessage registerRequest = new TCPMessage(MessageType.REGISTER_ESTUDANTE, payload);
            tempOut.writeObject(registerRequest);
            tempOut.flush();
            TCPMessage response = (TCPMessage) tempIn.readObject();
            return response.getType() == MessageType.REGISTER_SUCCESS;
        } catch (Exception e) {
            return false;
        }
    }

    public int registerDocente(Docente docente, String password, String codigoRegisto) {
        if (serverIp == null) return 2;
        try (Socket tempSocket = new Socket(serverIp, serverPort);
             ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream());
             ObjectInputStream tempIn = new ObjectInputStream(tempSocket.getInputStream()))
        {
            Object[] payload = { docente, password, codigoRegisto };
            TCPMessage registerRequest = new TCPMessage(MessageType.REGISTER_DOCENTE, payload);
            tempOut.writeObject(registerRequest);
            tempOut.flush();
            TCPMessage response = (TCPMessage) tempIn.readObject();
            if (response.getType() == MessageType.REGISTER_SUCCESS) return 0;
            String errorMsg = (String) response.getPayload();
            if (errorMsg.contains("Código")) return 1;
            return 2;
        } catch (Exception e) {
            return 2;
        }
    }

    public void closeConnection() {
        try {
            if (notificationListener != null) notificationListener.interrupt();
            if (out != null) out.close();
            if (in != null) in.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) tcpSocket.close();
        } catch (Exception e) { }
        finally {
            out = null; in = null; tcpSocket = null; notificationListener = null;
        }
    }

    public String createQuestion(Question question) {
        TCPMessage response = sendRequest(new TCPMessage(MessageType.CREATE_QUESTION_REQUEST, question));
        return (response != null && response.getType() == MessageType.CREATE_QUESTION_SUCCESS) ? (String) response.getPayload() : null;
    }

    public boolean submitAnswer(int idPergunta, String respostaLetra) {
        if (tcpSocket == null) return false;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.SUBMIT_ANSWER, new AnswerPayload(idPergunta, respostaLetra)));
        return (response != null && response.getType() == MessageType.SUBMIT_ANSWER_SUCCESS);
    }

    public List<Question> getMyQuestions(String filter) {
        if (tcpSocket == null) return null;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.GET_MY_QUESTIONS_REQUEST, filter));
        return (response != null && response.getType() == MessageType.GET_MY_QUESTIONS_SUCCESS) ? (List<Question>) response.getPayload() : null;
    }

    public Question getQuestionByCode(String accessCode) {
        if (tcpSocket == null) return null;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.GET_QUESTION_BY_CODE, accessCode));
        return (response != null && response.getType() == MessageType.GET_QUESTION_SUCCESS) ? (Question) response.getPayload() : null;
    }

    public boolean deleteQuestion(String accessCode) {
        if (tcpSocket == null) return false;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.DELETE_QUESTION_REQUEST, accessCode));
        return (response != null && response.getType() == MessageType.DELETE_QUESTION_SUCCESS);
    }

    public boolean editQuestion(String accessCode, Question newQuestionData) {
        if (tcpSocket == null) return false;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.EDIT_QUESTION_REQUEST, new Object[]{accessCode, newQuestionData}));
        return (response != null && response.getType() == MessageType.EDIT_QUESTION_SUCCESS);
    }

    public boolean updateProfileDocente(Docente docente, String newPassword) {
        if (tcpSocket == null) return false;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.EDIT_PROFILE_DOCENTE_REQUEST, new Object[]{docente, newPassword}));
        return (response != null && response.getType() == MessageType.EDIT_PROFILE_DOCENTE_SUCCESS);
    }

    public boolean updateProfileEstudante(Estudante estudante, String newPassword) {
        if (tcpSocket == null) return false;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.EDIT_PROFILE_ESTUDANTE_REQUEST, new Object[]{estudante, newPassword}));
        return (response != null && response.getType() == MessageType.EDIT_PROFILE_ESTUDANTE_SUCCESS);
    }

    public List<SubmittedAnswer> getMyAnswers() {
        if (tcpSocket == null) return null;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.GET_MY_ANSWERS_REQUEST));
        return (response != null && response.getType() == MessageType.GET_MY_ANSWERS_SUCCESS) ? (List<SubmittedAnswer>) response.getPayload() : null;
    }

    public static class QuestionFullReport implements java.io.Serializable {
        public Question question;
        public List<QuestionResult> results;
        public QuestionFullReport(Question q, List<QuestionResult> r) { this.question = q; this.results = r; }
    }

    public QuestionFullReport getQuestionResults(String accessCode) {
        if (tcpSocket == null) return null;
        TCPMessage response = sendRequest(new TCPMessage(MessageType.GET_QUESTION_RESULTS_REQUEST, accessCode));
        if (response != null && response.getType() == MessageType.GET_QUESTION_RESULTS_SUCCESS) {
            Object[] parts = (Object[]) response.getPayload();
            return new QuestionFullReport((Question) parts[0], (List<QuestionResult>) parts[1]);
        }
        return null;
    }

    private boolean reconnectAndReauthenticate() {
        System.err.println("[Failover] A tentar recuperar ligação...");
        String oldServerIp = this.currentServerIp;
        int oldServerPort = this.currentServerPort;
        closeConnection();
        if (!findServer()) return false;

        if (this.serverIp.equals(oldServerIp) && this.serverPort == oldServerPort) {
            try { Thread.sleep(20000); } catch (InterruptedException e) { return false; }
            if (!findServer()) return false;
        }
        this.currentServerIp = this.serverIp;
        this.currentServerPort = this.serverPort;

        if (lastEmail != null && lastPassword != null) {
            return login(this.lastEmail, this.lastPassword) != null;
        }
        return false;
    }

    private TCPMessage sendRequest(TCPMessage requestMsg) {
        try {
            responseQueue.clear();
            out.writeObject(requestMsg);
            out.flush();
            TCPMessage response = responseQueue.poll(20, TimeUnit.SECONDS);
            if (response == null) throw new Exception("Timeout");
            return response;
        } catch (Exception e) {
            if (reconnectAndReauthenticate()) {
                try {
                    responseQueue.clear();
                    out.writeObject(requestMsg);
                    out.flush();
                    return responseQueue.poll(20, TimeUnit.SECONDS);
                } catch (Exception ex) { }
            }
        }
        return null;
    }
}