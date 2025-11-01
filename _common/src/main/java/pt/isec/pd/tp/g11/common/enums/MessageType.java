package pt.isec.pd.tp.g11.common.enums;

import java.io.Serializable;

public enum MessageType implements Serializable {
    SERVER_REGISTER, // pedido para registo no servidor
    SERVER_REGISTER_OK, //pedido para registo no servidor aceite
    SERVER_HEARTBEAT, //envio do heartbeat
    CLIENT_REQUEST_SERVER, //pedido do cliente a perguntar quem é o servidor
    CLIENT_RESPONSE_SERVER, //resposta da diretoria com os dados do servidor

    // --- NOVOS TIPOS PARA TCP (LOGIN E REGISTO) ---
    LOGIN_REQUEST,      // Cliente -> Servidor (Payload: String[] {email, pass})
    REGISTER_ESTUDANTE, // Cliente -> Servidor (Payload: Estudante)
    REGISTER_DOCENTE,   // Cliente -> Servidor (Payload: Docente + código)

    LOGIN_SUCCESS,      // Servidor -> Cliente (Payload: User)
    LOGIN_FAILED,       // Servidor -> Cliente (Payload: String "Erro...")
    REGISTER_SUCCESS,   // Servidor -> Cliente (Sem payload)
    REGISTER_FAILED,    // Servidor -> Cliente (Payload: String "Erro...")

    // --- NOVOS TIPOS PARA PERGUNTAS ---
    CREATE_QUESTION_REQUEST, // Cliente -> Servidor (Payload: Question)
    CREATE_QUESTION_SUCCESS, // Servidor -> Cliente (Payload: String accessCode)
    CREATE_QUESTION_FAILED,  // Servidor -> Cliente (Payload: String "Erro...")

    // --- NOVOS TIPOS PARA RESPONDER (ESTUDANTE) ---
    GET_QUESTION_BY_CODE,      // Cliente -> Servidor (Payload: String codigoAcesso)
    GET_QUESTION_SUCCESS,    // Servidor -> Cliente (Payload: Question)
    GET_QUESTION_FAILED,     // Servidor -> Cliente (Payload: String "Erro...")

    SUBMIT_ANSWER,          // Cliente -> Servidor (Payload: AnswerPayload)
    SUBMIT_ANSWER_SUCCESS,  // Servidor -> Cliente (Sem payload)
    SUBMIT_ANSWER_FAILED,   // Servidor -> Cliente (Payload: String "Erro...")



    // --- Tipos Futuros para TCP (Perguntas, etc.) ---
    // CREATE_QUESTION, GET_QUESTIONS, SUBMIT_ANSWER, etc.

    ERROR // Para mensagens de erro genéricas
}
