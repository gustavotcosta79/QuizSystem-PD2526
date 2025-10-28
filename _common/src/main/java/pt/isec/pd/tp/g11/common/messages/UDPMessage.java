/*
 * Ficheiro: UDPMessage.java
 * Objetivo: Classe "envelope" para todas as mensagens UDP.
 * Responsabilidade: Conter o tipo de mensagem (do enum MessageType)
 * e o payload (os dados). Esta é a classe que será
 * serializada e enviada nos DatagramPackets.
 */

package pt.isec.pd.tp.g11.common.messages;

import pt.isec.pd.tp.g11.common.enums.MessageType;

import java.io.Serializable;

public class UDPMessage implements Serializable {
    private static final long serialVersionUID = 1L; // Importante para serialização

    private final MessageType type;
    private final String[] payload; // Usar String[] é flexível para enviar dados

    public UDPMessage(MessageType type, String... payload) {
        this.type = type;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public String[] getPayload() {
        return payload;
    }
}