package pt.isec.pd.tp.g11.common.enums;

import java.io.Serializable;

public enum MessageType implements Serializable {
    SERVER_REGISTER, // pedido para registo no servidor
    SERVER_REGISTER_OK, //pedido para registo no servidor aceite
    SERVER_HEARTBEAT, //envio do heartbeat
    CLIENT_REQUEST_SERVER, //pedido do cliente a perguntar quem é o servidor
    CLIENT_RESPONSE_SERVER, //resposta da diretoria com os dados do servidor

    ERROR // Para mensagens de erro genéricas
}
