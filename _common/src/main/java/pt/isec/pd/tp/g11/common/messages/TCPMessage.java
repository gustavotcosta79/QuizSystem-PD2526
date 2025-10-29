/*
 * Ficheiro: TCPMessage.java
 * Objetivo: Classe "envelope" para todas as mensagens TCP.
 * Responsabilidade: Conter o tipo de mensagem (do enum MessageType)
 * e o payload (qualquer objeto serializável). Esta é a classe
 * que será enviada/recebida via ObjectOutputStream/ObjectInputStream.
 */
package pt.isec.pd.tp.g11.common.messages;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import java.io.Serializable;

public class TCPMessage implements Serializable {

    private static final long serialVersionUID = 10L;

    private final MessageType type;
    private final Serializable payload; // Pode ser QUALQUER objeto (String[], User, etc.)

    public TCPMessage(MessageType type, Serializable payload) {
        this.type = type;
        this.payload = payload;
    }

    public TCPMessage(MessageType type) { /// para quando ja estiver registado  (acho eu)
        this.type = type;
        this.payload = null;
    }

    public MessageType getType() {
        return type;
    }

    public Serializable getPayload() {
        return payload;
    }
}