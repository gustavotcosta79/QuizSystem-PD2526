package pt.isec.pd.tp.g11.client.communication;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.TCPMessage;

import java.io.ObjectInputStream;
import java.util.concurrent.BlockingQueue;

public class NotificationListener extends Thread {
    private final ObjectInputStream in;
    private final BlockingQueue<TCPMessage> responseQueue;

    // Recebe o stream de entrada e uma Fila para colocar as respostas normais
    public NotificationListener(ObjectInputStream in, BlockingQueue<TCPMessage> responseQueue) {
        this.in = in;
        this.responseQueue = responseQueue;
        setDaemon(true); // Morre se a main thread morrer
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                // ESTA É A ÚNICA LINHA QUE LÊ DO SOCKET EM TODO O CLIENTE
                Object obj = in.readObject();

                if (obj instanceof TCPMessage msg) {
                    if (msg.getType() == MessageType.NOTIFICATION) {
                        // 1. É NOTIFICAÇÃO: Mostrar logo no ecrã!
                        String texto = (String) msg.getPayload();
                        System.out.println("\n\n>>> [NOTIFICAÇÃO] " + texto + " <<<\n");
                        System.out.print("Escolha: "); // Repor o prompt para ficar bonito
                    } else {
                        // 2. É RESPOSTA A UM PEDIDO: Mandar para a fila
                        responseQueue.put(msg);
                    }
                }
            }
        } catch (Exception e) {
            // O socket fechou ou houve erro. O ServerConnection vai tratar disto no Failover.
        }
    }
}