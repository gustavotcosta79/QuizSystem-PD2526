/*\
 * Ficheiro: ConsoleUI.java
 * [cite_start]Objetivo: Componente de Vista[cite: 172].
 * Responsabilidade: Gerir toda a interação com o utilizador
 * (menus, leitura de dados). Corre na sua própria thread.
 */
package pt.isec.pd.tp.g11.client.view;

import pt.isec.pd.tp.g11.client.communication.ServerConnection;
import java.util.Scanner;

public class ConsoleUI implements Runnable {

    private final ServerConnection connection;
    private final Scanner scanner;

    public ConsoleUI(ServerConnection connection) {
        this.connection = connection;
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void run() {
        System.out.println("Bem-vindo ao Sistema de Perguntas!");

        // 1. Primeiro passo obrigatório: encontrar o servidor
        if (!connection.findServer()) {
            System.err.println("Não foi possível encontrar um servidor ativo. A aplicação vai terminar.");
            return;
        }

        // 2. Se encontrou, entra no menu principal
        mainMenuLoop();

        System.out.println("A sair...");
        scanner.close();
    }

    private void mainMenuLoop() {
        int choice;
        do {
            System.out.println("\n--- Menu Principal (Esqueleto) ---");
            System.out.println("1. Testar Ligação TCP ao Servidor");
            System.out.println("2. (TODO: Login)");
            System.out.println("3. (TODO: Registar)");
            System.out.println("0. Sair");
            System.out.print("Escolha: ");

            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                choice = -1;
            }

            switch (choice) {
                case 1:
                    // A Vista não sabe o que é TCP. Ela só
                    // dá a ordem ao componente de comunicação.
                    connection.testTCPConnection();
                    break;
                case 2:
                    System.out.println("Login (ainda não implementado).");
                    // handleLogin();
                    break;
                case 3:
                    System.out.println("Registar (ainda não implementado).");
                    break;
                case 0:
                    System.out.println("Até à próxima!");
                    break;
                default:
                    System.out.println("Opção inválida.");
                    break;
            }
        } while (choice != 0);
    }

    // TODO: Funções futuras
    /*
    private void handleLogin() {
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        String pass = scanner.nextLine();
        
        User user = connection.login(email, pass);
        
        if(user != null) {
            System.out.println("Login com sucesso! Bem-vindo, " + user.getNome());
            // loggedInMenu(user);
        } else {
            System.out.println("Login falhou. Credenciais erradas ou servidor indisponível.");
        }
    }
    */
}