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
        // Ler os argumentos (IP:Porto da Diretoria)
        if (args.length != 1) {
            System.err.println("Uso: java -jar client.jar <dir_ip:dir_port>");
            return;
        }

        try {
            // Criar os dois componentes principais

            // O componente de Comunicação (a "lógica" da rede)
            ServerConnection connection = new ServerConnection(args[0]);

            ConsoleUI ui = new ConsoleUI(connection);

            // Iniciar a thread da Vista
            new Thread(ui).start();

        } catch (Exception e) {
            System.err.println("Erro ao arrancar o cliente: " + e.getMessage());
        }
    }
}