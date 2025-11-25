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

    // --- NOVOS TIPOS PARA LISTAR PERGUNTAS (DOCENTE) ---
    GET_MY_QUESTIONS_REQUEST,  // Cliente -> Servidor (Payload: String filtro, ex: "ALL", "ACTIVE", "FUTURE", "PAST")
    GET_MY_QUESTIONS_SUCCESS, // Servidor -> Cliente (Payload: List<Question>)  ---> se foi sucesso e consegue retornar a lista
    GET_MY_QUESTIONS_FAILED,  // Servidor -> Cliente (Payload: String "Erro...") --> se foi insucesso e
    // não consegue retornar a lista
    DELETE_QUESTION_REQUEST,  // Cliente -> Servidor (Payload: Integer idPergunta)
    DELETE_QUESTION_SUCCESS, // Servidor -> Cliente (Sem payload)
    DELETE_QUESTION_FAILED,  // Servidor -> Cliente (Payload: String "Erro...")

    EDIT_QUESTION_REQUEST,  // Cliente -> Servidor (Payload: Object[] {String accessCode, Question newData})
    EDIT_QUESTION_SUCCESS, // Servidor -> Cliente (Sem payload)
    EDIT_QUESTION_FAILED,   // Servidor -> Cliente (Payload: String "Erro...")


    EDIT_PROFILE_DOCENTE_REQUEST,   // Cliente -> Servidor (Payload: Object[] {Docente, String newPassword})
    EDIT_PROFILE_DOCENTE_SUCCESS, // Servidor -> Cliente
    EDIT_PROFILE_DOCENTE_FAILED,  // Servidor -> Cliente (Payload: String "Erro...")

    EDIT_PROFILE_ESTUDANTE_REQUEST, // Cliente -> Servidor (Payload: Object[] {Estudante, String newPassword})
    EDIT_PROFILE_ESTUDANTE_SUCCESS, // Servidor -> Cliente
    EDIT_PROFILE_ESTUDANTE_FAILED,  // Servidor -> Cliente (Payload: String "Erro...")

    GET_MY_ANSWERS_REQUEST,   // Cliente -> Servidor (Sem payload, usa o ID da sessão)
    GET_MY_ANSWERS_SUCCESS, // Servidor -> Cliente (Payload: List<SubmittedAnswer>)
    GET_MY_ANSWERS_FAILED,  // Servidor -> Cliente (Payload: String "Erro...")

    GET_QUESTION_RESULTS_REQUEST,   // Cliente -> Servidor (Payload: String accessCode)
    GET_QUESTION_RESULTS_SUCCESS, // Servidor -> Cliente (Payload: List<QuestionResult>)
    GET_QUESTION_RESULTS_FAILED,  // Servidor -> Cliente (Payload: String "Erro...")


    SERVER_UNREGISTER,
    SERVER_SYNC_FAILED,

    NOTIFICATION, // Payload: String (a mensagem para mostrar)


    ERROR // Para mensagens de erro genéricas
}
