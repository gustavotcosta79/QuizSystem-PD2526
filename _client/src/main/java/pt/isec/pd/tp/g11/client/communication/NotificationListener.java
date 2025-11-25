package pt.isec.pd.tp.g11.client.communication;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.TCPMessage;

import java.io.ObjectInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

public class NotificationListener extends Thread {
    private final ObjectInputStream in;
    private final BlockingQueue<TCPMessage> responseQueue;
    private final Consumer<String> notificationHandler; // CALLBACK PARA A GUI

    // Construtor atualizado
    public NotificationListener(ObjectInputStream in, BlockingQueue<TCPMessage> responseQueue, Consumer<String> notificationHandler) {
        this.in = in;
        this.responseQueue = responseQueue;
        this.notificationHandler = notificationHandler;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                Object obj = in.readObject();

                if (obj instanceof TCPMessage msg) {
                    if (msg.getType() == MessageType.NOTIFICATION) {
                        String texto = (String) msg.getPayload();

                        // SE TIVERMOS UM HANDLER (GUI), CHAMAMOS. SENÃO, IMPRIME NA CONSOLA.
                        if (notificationHandler != null) {
                            notificationHandler.accept(texto);
                        } else {
                            System.out.println("\n\n>>> [NOTIFICAÇÃO] " + texto + " <<<\n");
                        }

                    } else {
                        responseQueue.put(msg);
                    }
                }
            }
        } catch (Exception e) {
            // Socket fechado ou erro
        }
    }
}