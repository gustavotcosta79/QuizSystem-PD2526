/*
 * Ficheiro: HeartbeatService.java
 * Objetivo: Thread responsável por enviar heartbeats periódicos (a cada 5s)
 * para o Serviço de Diretoria, anunciando os portos TCP deste servidor.
 */
package pt.isec.pd.tp.g11.server.net;

import pt.isec.pd.tp.g11.common.enums.MessageType;
import pt.isec.pd.tp.g11.common.messages.UDPMessage;
import pt.isec.pd.tp.g11.common.utils.SerializationUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class HeartbeatService extends Thread {

    public static final int HEARTBEAT_INTERVAL_MS = 5000; // 5 segundos

    private final InetAddress directoryAddress;
    private final int directoryPort;
    private final int serverClientPort;
    private final int serverDbPort;

    private boolean isRunning = true;

    public HeartbeatService(InetAddress dirAddress, int dirPort, int serverClientPort, int serverDbPort) {
        this.directoryAddress = dirAddress;
        this.directoryPort = dirPort;
        this.serverClientPort = serverClientPort;
        this.serverDbPort = serverDbPort;
        setDaemon(true); // Para que esta thread não impeça o servidor de fechar
    }

    public void stopHeartbeat() {
        this.isRunning = false;
        interrupt(); // Interrompe o Thread.sleep()
    }

    @Override
    public void run() {
        // Usamos try-with-resources para garantir que o socket UDP fecha
        try (DatagramSocket socket = new DatagramSocket()) { // Porta automática

            while (isRunning) {
                try {
                    // 1. Criar o OBJETO da mensagem
                    // Usamos HEARTBEAT para registo e atualização
                    UDPMessage msg = new UDPMessage(MessageType.SERVER_HEARTBEAT,
                            String.valueOf(serverClientPort),
                            String.valueOf(serverDbPort)
                    );

                    // 2. SERIALIZAR o objeto (com Utils)
                    byte[] data = SerializationUtils.serialize(msg);

                    // 3. Enviar o PACOTE de bytes
                    DatagramPacket packet = new DatagramPacket(data, data.length, directoryAddress, directoryPort);
                    socket.send(packet);

                    System.out.println("[Heartbeat] Anunciado ao " + directoryAddress.getHostAddress() + ":" + directoryPort);

                    // 4. Esperar pela resposta (opcional, mas bom para debug)
                    // O teu ServerListManager responde com o servidor principal
                    byte[] buffer = new byte[4096];
                    DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(responsePacket); // Espera pela resposta

                    // TODO: Processar a resposta (saber quem é o principal)

                    // 5. Dormir 5 segundos
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);

                } catch (InterruptedException e) {
                    if (isRunning) System.err.println("Heartbeat interrompido.");
                } catch (Exception e) {
                    System.err.println("Erro no HeartbeatService: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Erro fatal no HeartbeatService (não conseguiu abrir socket): " + e.getMessage());
        }
        System.out.println("[Heartbeat] Serviço parado.");
    }
}