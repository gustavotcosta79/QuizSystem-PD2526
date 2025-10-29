package pt.isec.pd.tp.g11.client.view;

import pt.isec.pd.tp.g11.client.communication.ServerConnection;
import pt.isec.pd.tp.g11.common.model.User; // Importar User
import java.util.Scanner;

public class ConsoleUI implements Runnable {

    private final ServerConnection connection;
    private final Scanner scanner;
    private User loggedInUser = null; // Para saber se estamos logados

    public ConsoleUI(ServerConnection connection) {
        this.connection = connection;
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void run() {
        System.out.println("Bem-vindo ao Sistema de Perguntas!");

        // 1. Encontrar o servidor (passo UDP obrigatório)
        if (!connection.findServer()) {
            System.err.println("Não foi possível encontrar um servidor ativo. A aplicação vai terminar.");
            scanner.close(); // Fechar o scanner
            return;
        }

        // 2. Entrar no menu principal (Login/Registo/Sair)
        mainMenuLoop();

        // 3. Antes de sair, fechar a ligação TCP se existir
        connection.closeConnection();
        scanner.close(); // Fechar o scanner
        System.out.println("Aplicação cliente terminada.");
    }

    private void mainMenuLoop() {
        int choice;
        do {
            System.out.println("\n--- Menu Principal ---");
            System.out.println("1. Login");
            System.out.println("2. Registar Estudante (TODO)");
            System.out.println("3. Registar Docente (TODO)");
            System.out.println("0. Sair");
            System.out.print("Escolha: ");

            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                choice = -1;
            }

            switch (choice) {
                case 1:
                    handleLogin();
                    // Se o login for bem sucedido, loggedInUser não será null
                    if (loggedInUser != null) {
                        // TODO: Entrar no menu apropriado (Docente/Estudante)
                        System.out.println("A entrar no menu do utilizador (TODO)...");
                        // loggedInMenu(); // Chama o próximo menu
                        // Se loggedInMenu retornar (logout), voltamos aqui
                        loggedInUser = null; // Resetar user no logout
                    }
                    break;
                case 2:
                    System.out.println("Registar Estudante (ainda não implementado).");
                    break;
                case 3:
                    System.out.println("Registar Docente (ainda não implementado).");
                    break;
                case 0:
                    System.out.println("A sair...");
                    break;
                default:
                    System.out.println("Opção inválida.");
                    break;
            }
        } while (choice != 0);
    }

    /**
     * Pede os dados ao utilizador e chama o componente de comunicação.
     * Atualiza loggedInUser se o login for bem-sucedido.
     */
    private void handleLogin() {
        System.out.println("\n--- Login ---");
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        String pass = scanner.nextLine();

        // A "Vista" chama o método de "Comunicação"
        User user = connection.login(email, pass);

        if (user != null) {
            System.out.println("Login com sucesso! Bem-vindo, " + user.getNome());
            this.loggedInUser = user; // Guarda o utilizador logado
        } else {
            System.out.println("Login falhou. Verifique as credenciais ou o servidor.");
            this.loggedInUser = null; // Garante que não fica logado
        }
    }

    // TODO: Implementar loggedInMenu() que mostra opções diferentes
    //       para Docente e Estudante (instanceof loggedInUser).
    //       Este menu deve ter a opção "Logout" que faz choice = 0
    //       e talvez chame connection.logout() no futuro.
}