/*
 * Ficheiro: Main.java
 * Objetivo: Ponto de entrada (main) para a aplicação "Cliente".
 * Responsabilidade: Ler os argumentos da linha de comando,
 * criar os componentes de Comunicação (ServerConnection)
 * e da Vista (ConsoleUI), e iniciar a thread da Vista.
 */
package pt.isec.pd.tp.g11.client;

import pt.isec.pd.tp.g11.client.communication.ServerConnection;
import pt.isec.pd.tp.g11.client.view.ConsoleUI;

public class MainClient {

    public static void main(String[] args) {
        // 1. Ler os argumentos (IP:Porto da Diretoria)
        if (args.length != 1) {
            System.err.println("Uso: java -jar client.jar <dir_ip:dir_port>");
            return;
        }

        try {
            // 2. Criar os dois componentes principais

            // O componente de Comunicação (a "lógica" da rede)
            ServerConnection connection = new ServerConnection(args[0]);

            // O componente da Vista (a "interface" de consola)
            // Passamos a 'connection' para que a UI possa
            // dar-lhe ordens (ex: "faz login", "envia pergunta")
            ConsoleUI ui = new ConsoleUI(connection);

            // 3. Iniciar a thread da Vista
            // (A Vista corre numa thread separada para não
            // bloquear a receção de notificações assíncronas no futuro)
            new Thread(ui).start();

        } catch (Exception e) {
            System.err.println("Erro ao arrancar o cliente: " + e.getMessage());
        }
    }
}