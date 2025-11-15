package pt.isec.pd.tp.g11.server.net;

import java.io.*;
import java.net.Socket;

public class DbSyncHandler extends Thread {
    private final Socket socket;
    private final String dbFilePath;

    public DbSyncHandler(Socket socket, String dbFilePath) {
        this.socket = socket;
        this.dbFilePath = dbFilePath;
    }

    @Override
    public void run() {
        File dbFile = new File(dbFilePath);

        try (OutputStream out = socket.getOutputStream();
             FileInputStream fileIn = new FileInputStream(dbFile)) {

            // 1. Verificar se o ficheiro existe
            if (!dbFile.exists()) {
                System.err.println("[DbSyncHandler] Erro crítico: O ficheiro de BD não existe em " + dbFilePath);
                socket.close();
                return;
            }

            System.out.println("[DbSyncHandler] A iniciar envio da BD (" + dbFile.length() + " bytes)...");

            // 2. (Opcional) Enviar o tamanho do ficheiro primeiro, se quiseres fazer barra de progresso no backup
            // DataOutputStream dos = new DataOutputStream(out);
            // dos.writeLong(dbFile.length());

            // 3. Ler do ficheiro e escrever no socket (Buffer de 4KB)
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();

            System.out.println("[DbSyncHandler] Transferência concluída com sucesso.");

        } catch (IOException e) {
            System.err.println("[DbSyncHandler] Erro na transferência: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) { /* ignorar */ }
        }
    }
}