/*
 * Ficheiro: MainDiretoria.java
 * Objetivo: Ponto de entrada (main) para a aplicação "Serviço de Diretoria".
 * Responsabilidade: Ler o porto UDP da linha de comandos (com um valor
 * por defeito) e iniciar a thread principal do serviço
 * (a classe DirectoryService).
 */

package pt.isec.pd.tp.g11.directory_service;

import pt.isec.pd.tp.g11.directory_service.net.DirectoryService;

public class MainDirectory {

    public static final int DEFAULT_PORT = 5000; // Porto por defeito se nenhum for fornecido

    public static void main(String[] args) {
        int port;

        if (args.length == 1) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Porto inválido. A usar porto por defeito: " + DEFAULT_PORT);
                port = DEFAULT_PORT;
            }
        } else {
            System.out.println("A usar porto por defeito: " + DEFAULT_PORT);
            port = DEFAULT_PORT;
        }

        // O enunciado diz que o porto é passado na linha de comando
        // Esta lógica garante que temos um porto

        System.out.println("[MainDiretoria] A iniciar serviço no porto UDP " + port);

        // Inicia a thread principal do serviço de diretoria
        DirectoryService service = new DirectoryService(port);
        service.start(); // Inicia o método run() numa nova Thread
    }
}