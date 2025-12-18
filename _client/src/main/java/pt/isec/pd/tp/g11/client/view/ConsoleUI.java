package pt.isec.pd.tp.g11.client.view;

import pt.isec.pd.tp.g11.client.communication.ServerConnection;
import pt.isec.pd.tp.g11.common.model.*;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConsoleUI implements Runnable {

    private final ServerConnection connection;
    private final Scanner scanner;
    private User loggedInUser = null;

    public ConsoleUI(ServerConnection connection) {
        this.connection = connection;
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void run() {
        System.out.println("Bem-vindo ao Sistema de Perguntas!");

        if (!connection.findServer()) {
            System.err.println("Não foi possível encontrar um servidor ativo. A aplicação vai terminar.");
            scanner.close();
            return;
        }

        mainMenuLoop();

        connection.closeConnection();
        scanner.close();
        System.out.println("Aplicação cliente terminada.");
    }

    private void mainMenuLoop() {
        int choice;
        do {
            System.out.println("\n--- Menu Principal ---");
            System.out.println("1. Login");
            System.out.println("2. Registar Estudante");
            System.out.println("3. Registar Docente");
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
                    // chamar o menu de logado
                    if (loggedInUser != null) {
                        loggedInMenu(); // Chama o próximo menu
                        // Quando loggedInMenu retornar (ex: no logout),
                        // o loop volta ao Menu Principal
                        loggedInUser = null; // Resetar user no logout
                    }
                    break;
                case 2:
                    System.out.println("Registar Estudante.");
                    handleRegisterEstudante();
                    break;
                case 3:
                    System.out.println("Registar Docente.");
                    handleRegisterDocente();
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

    // MÉTODOS DE AUTENTICAÇÃO

    private void handleLogin() {
        System.out.println("\n--- Login ---");
        System.out.print("Email: ");
        String email = scanner.nextLine();
        System.out.print("Password: ");
        String pass = scanner.nextLine();

        User user = connection.login(email, pass);

        if (user != null) {
            System.out.println("Login com sucesso! Bem-vindo, " + user.getNome());
            this.loggedInUser = user;
        } else {
            System.out.println("Login falhou. Verifique as credenciais ou o servidor.");
            this.loggedInUser = null;
        }
    }

    private void handleRegisterEstudante() {
        System.out.println("\n--- Registo de Novo Estudante ---");
        try {
            System.out.print("Nome Completo: ");
            String nome = scanner.nextLine();
            System.out.print("Email: ");
            String email = scanner.nextLine();
            System.out.print("Número de Estudante: ");
            String numero = scanner.nextLine();
            System.out.print("Password: ");
            String pass1 = scanner.nextLine();
            System.out.print("Confirme a Password: ");
            String pass2 = scanner.nextLine();

            if (!pass1.equals(pass2)) {
                System.err.println("As passwords não coincidem. Tente novamente."); return;
            }
            if (nome.isEmpty() || email.isEmpty() || numero.isEmpty() || pass1.isEmpty()) {
                System.err.println("Todos os campos são obrigatórios. Tente novamente."); return;
            }

            Estudante novoEstudante = new Estudante(0, nome, email, numero);

            System.out.println("A tentar registar. Por favor, aguarde...");
            if (connection.registerEstudante(novoEstudante, pass1)) {
                System.out.println("Registo efetuado com sucesso! Já pode fazer login.");
            } else {
                System.out.println("Falha no registo. O email ou número de estudante podem já estar em uso.");
            }
        } catch (Exception e) {
            System.err.println("Ocorreu um erro inesperado durante o registo.");
        }
    }

    private void handleRegisterDocente() {
        System.out.println("\n--- Registo de Novo Docente ---");
        try {
            System.out.print("Nome Completo: ");
            String nome = scanner.nextLine();
            System.out.print("Email: ");
            String email = scanner.nextLine();
            System.out.print("Password: ");
            String pass1 = scanner.nextLine();
            System.out.print("Confirme a Password: ");
            String pass2 = scanner.nextLine();
            System.out.print("Código de Registo de Docente: ");
            String codigo = scanner.nextLine();

            if (!pass1.equals(pass2)) {
                System.err.println("As passwords não coincidem."); return;
            }
            if (nome.isEmpty() || email.isEmpty() || pass1.isEmpty() || codigo.isEmpty()) {
                System.err.println("Todos os campos são obrigatórios."); return;
            }

            Docente novoDocente = new Docente(0, nome, email);

            System.out.println("A tentar registar. Por favor, aguarde...");
            int resultCode = connection.registerDocente(novoDocente, pass1, codigo);

            if (resultCode == 0) {
                System.out.println("Registo efetuado com sucesso! Já pode fazer login.");
            } else if (resultCode == 1) {
                System.out.println("Falha no registo. O Código de Registo de Docente está incorreto.");
            } else {
                System.out.println("Falha no registo. O email pode já estar em uso.");
            }
        } catch (Exception e) {
            System.err.println("Ocorreu um erro inesperado durante o registo.");
        }
    }

    //  NOVOS MÉTODOS (MENU LOGADO)

    /**
     * Menu principal para um utilizador que já fez login.
     * Direciona para o menu de Docente ou Estudante.
     */
    private void loggedInMenu() {
        if (loggedInUser instanceof Docente) {
            menuDocente();
        } else if (loggedInUser instanceof Estudante) {
            menuEstudante();
        }
    }

    /**
     * Menu de funcionalidades para o Docente.
     */
    private void menuDocente() {
        int choice;
        do {
            System.out.println("\n--- Menu Docente (" + loggedInUser.getNome() + ") ---");
            System.out.println("1. Criar Nova Pergunta");
            System.out.println("2. Listar Minhas Perguntas");
            System.out.println("3. Editar Pergunta");
            System.out.println("4. Eliminar Pergunta ");
            System.out.println("5. Ver Resultados de Pergunta Expirada");// filtro para ver ou exportar para csv
            System.out.println("6. Editar Registo Pessoal (Docente)\n");
            System.out.println("0. Logout");
            System.out.print("Escolha: ");

            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) { choice = -1; }

            switch (choice) {
                case 1:
                    handleCreateQuestion();
                    break;
                case 2:
                    handleListMyQuestions();
                    break;
                case 3:
                    handleEditQuestion();
                    break;
                case 4:
                    handleDeleteQuestion();
                    break;
                case 5:
                    handleViewQuestionResults();
                    break;
                case 6:
                    handleEditProfileDocente();
                    break;
                case 0:
                    System.out.println("A fazer logout...");
                    connection.closeConnection();

                    break;
                default:
                    System.out.println("Opção inválida.");
                    break;
            }
        } while (choice != 0);
    }

    /**
     * Menu de funcionalidades para o Estudante.
     */
        private void menuEstudante() {
        int choice;
        do {
            System.out.println("\n--- Menu Estudante (" + loggedInUser.getNome() + ") ---");
            System.out.println("1. Responder a Pergunta"); // Esta sincronizado
            System.out.println("2. Ver Respostas Submetidas"); //(leitura)
            System.out.println("3. Editar Registo Pessoal (Estudante)\n"); // sinconizado
            System.out.println("0. Logout");
            System.out.print("Escolha: ");

            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) { choice = -1; }

            switch (choice) {
                case 1:
                    handleAnswerQuestion();
                    break;
                case 2:
                     handleViewMyAnswers();
                    break;
                case 3:
                    handleEditProfileEstudante();
                    break;
                case 0:
                    System.out.println("A fazer logout...");
                    connection.closeConnection();
                    break;
                default:
                    System.out.println("Opção inválida.");
                    break;
            }
        } while (choice != 0);
    }

    /**
     * Recolhe os dados para uma nova pergunta e
     * chama o componente de comunicação.
     */
    private void handleCreateQuestion() {
        System.out.println("\n--- Criar Nova Pergunta ---");
        try {
            // Recolher dados da Pergunta
            System.out.print("Enunciado: ");
            String enunciado = scanner.nextLine();

            // Recolher Opções
            List<Option> options = new ArrayList<>();
            System.out.print("Quantas opções? (Min 2): ");
            int numOpcoes = Integer.parseInt(scanner.nextLine());
            if (numOpcoes < 2) {
                System.err.println("Uma pergunta deve ter pelo menos 2 opções.");
                return;
            }

            char letra = 'a';
            for (int i = 0; i < numOpcoes; i++) {
                System.out.print("Texto da opção " + letra + ": ");
                String textoOpcao = scanner.nextLine();
                options.add(new Option(String.valueOf(letra), textoOpcao));
                letra++;
            }

            System.out.print("Qual a opção correta? (ex: 'a'): ");
            String respostaCerta = scanner.nextLine().trim().toLowerCase();

            // Recolher Datas

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

            System.out.print("Data/Hora de Início (dd-MM-yyyy HH:mm): ");
            LocalDateTime inicio = LocalDateTime.parse(scanner.nextLine(), formatter);

            System.out.print("Data/Hora de Fim (dd-MM-yyyy HH:mm): ");
            LocalDateTime fim = LocalDateTime.parse(scanner.nextLine(), formatter);

            if (fim.isBefore(inicio)) {
                System.err.println("A data de fim não pode ser anterior à data de início.");
                return;
            }

            // Criar o objeto Question
            Question newQuestion = new Question(enunciado, inicio, fim, respostaCerta, options);

            // Chamar o componente de comunicação
            System.out.println("A enviar pergunta para o servidor...");

            String accessCode = connection.createQuestion(newQuestion);

            if (accessCode != null) {
                System.out.println("Pergunta criada com sucesso!");
                System.out.println("O CÓDIGO DE ACESSO é: " + accessCode);
            } else {
                System.out.println("Falha ao criar a pergunta.");
            }

        } catch (java.time.format.DateTimeParseException e) {
            System.err.println("Formato de data inválido. Use dd-MM-yyyy HH:mm.");
        } catch (NumberFormatException e) {
            System.err.println("Número de opções inválido.");
        } catch (Exception e) {
            System.err.println("Ocorreu um erro inesperado: " + e.getMessage());
        }
    }

    /**
     * Recolhe o código de uma pergunta, pede ao servidor,
     * mostra-a ao estudante e submete a resposta.
     */
    private void handleAnswerQuestion() {
        System.out.println("\n--- Responder a Pergunta ---");
        System.out.print("Insira o Código de Acesso da Pergunta: ");
        String accessCode = scanner.nextLine().trim().toUpperCase();

        // Pedir a pergunta ao servidor
        System.out.println("A procurar a pergunta...");
        Question q = connection.getQuestionByCode(accessCode);

        // Verificar se a pergunta é válida
        if (q == null) {
            System.err.println("Pergunta não encontrada. O código pode estar errado ou a pergunta pode não estar ativa.");
            return;
        }

        // Mostrar a pergunta e as opções
        System.out.println("\nPERGUNTA ENCONTRADA!");
        System.out.println("---------------------------------");
        System.out.println("Enunciado: " + q.getEnunciado());
        System.out.println("Opções:");
        for (Option op : q.getOptions()) {
            System.out.println("  " + op.getLetter() + ") " + op.getTextOption());
        }
        System.out.println("---------------------------------");
        System.out.print("A sua resposta (ex: 'a'): ");
        String resposta = scanner.nextLine().trim().toLowerCase();

        // Submeter a resposta
        System.out.println("A submeter resposta...");
        if (connection.submitAnswer(q.getId(), resposta)) {
            System.out.println("Resposta submetida com sucesso!");
        } else {
            System.err.println("Falha ao submeter a resposta. (Já respondeu ou a pergunta expirou?)");
        }
    }

    /**
     * Pede ao utilizador um filtro e lista as suas perguntas.
     */
    private void handleListMyQuestions() {
        System.out.println("\n--- Listar Minhas Perguntas ---");
        System.out.println("Aplicar filtro:");
        System.out.println(" 1. Todas");
        System.out.println(" 2. Ativas (a decorrer)");
        System.out.println(" 3. Futuras (ainda não começaram)");
        System.out.println(" 4. Expiradas (já terminaram)");
        System.out.print("Escolha o filtro: ");

        String filter = "ALL"; // Default
        try {
            int choice = Integer.parseInt(scanner.nextLine());
            switch (choice) {
                case 2: filter = "ACTIVE"; break;
                case 3: filter = "FUTURE"; break;
                case 4: filter = "PAST"; break;
            }
        } catch (Exception e) {  }

        System.out.println("A obter perguntas do servidor...");
        List<Question> questions = connection.getMyQuestions(filter);

        if (questions == null) {
            System.err.println("Erro ao obter a lista de perguntas.");
            return;
        }

        if (questions.isEmpty()) {
            System.out.println("Nenhuma pergunta encontrada com este filtro.");
            return;
        }

        System.out.println("\n--- As Suas Perguntas (" + filter + ") ---");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (Question q : questions) {
            System.out.println("---------------------------------");
            System.out.println(" ID: " + q.getId() + " | Código: " + q.getAccessCode());
            System.out.println(" Pergunta: " + q.getEnunciado());
            System.out.println(" Início: " + q.getBeginDateHour().format(formatter));
            System.out.println(" Fim: " + q.getEndDateHour().format(formatter));
        }
        System.out.println("---------------------------------");
    }

    private void handleDeleteQuestion() {
        System.out.println("\n--- Eliminar Pergunta ---");
        System.out.println("AVISO: Só pode eliminar perguntas que AINDA NÃO TENHAM RESPOSTAS.");
        // ALTERAÇÃO AQUI: Pede o Código
        System.out.print("Insira o CÓDIGO DE ACESSO da pergunta a eliminar: ");

        try {
            String code = scanner.nextLine().trim().toUpperCase(); // ALTERAÇÃO AQUI

            if (code.isEmpty()) {
                System.err.println("Código inválido.");
                return;
            }

            System.out.println("A tentar eliminar pergunta " + code + "...");

            if (connection.deleteQuestion(code)) { // ALTERAÇÃO AQUI
                System.out.println("Pergunta eliminada com sucesso.");
            } else {
                System.err.println("Não foi possível eliminar a pergunta (Verifique se é sua e se não tem respostas).");
            }
        } catch (Exception e) { // Apanha outros erros
            System.err.println("Erro: " + e.getMessage());
        }
    }


    private void handleEditQuestion() {
        System.out.println("\n--- Editar Pergunta ---");
        System.out.println("AVISO: Só pode editar perguntas que AINDA NÃO TENHAM RESPOSTAS.");
        System.out.print("Insira o CÓDIGO DE ACESSO da pergunta que quer editar: ");

        String accessCode = scanner.nextLine().trim().toUpperCase();
        if (accessCode.isEmpty()) {
            System.err.println("Código inválido.");
            return;
        }

        try {
            System.out.println("--- Insira os NOVOS dados para a pergunta " + accessCode + " ---");

            // Recolher novos dados da Pergunta
            System.out.print("Novo Enunciado: ");
            String enunciado = scanner.nextLine();

            // Recolher Novas Opções
            List<Option> options = new ArrayList<>();
            System.out.print("Quantas opções? (Min 2): ");
            int numOpcoes = Integer.parseInt(scanner.nextLine());
            if (numOpcoes < 2) {
                System.err.println("Uma pergunta deve ter pelo menos 2 opções.");
                return;
            }

            char letra = 'a';
            for (int i = 0; i < numOpcoes; i++) {
                System.out.print("Texto da opção " + letra + ": ");
                String textoOpcao = scanner.nextLine();
                options.add(new Option(String.valueOf(letra), textoOpcao));
                letra++;
            }

            System.out.print("Qual a nova opção correta? (ex: 'a'): ");
            String respostaCerta = scanner.nextLine().trim().toLowerCase();

            // Recolher Novas Datas
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            System.out.print("Nova Data/Hora de Início (dd-MM-yyyy HH:mm): ");
            LocalDateTime inicio = LocalDateTime.parse(scanner.nextLine(), formatter);
            System.out.print("Nova Data/Hora de Fim (dd-MM-yyyy HH:mm): ");
            LocalDateTime fim = LocalDateTime.parse(scanner.nextLine(), formatter);

            if (fim.isBefore(inicio)) {
                System.err.println("A data de fim não pode ser anterior à data de início.");
                return;
            }

            // Criar o objeto Question com os novos dados
            Question newQuestionData = new Question(enunciado, inicio, fim, respostaCerta, options);

            // Chamar o componente de comunicação
            System.out.println("A enviar dados de edição para o servidor...");
            if (connection.editQuestion(accessCode, newQuestionData)) {
                System.out.println("Pergunta editada com sucesso!");
            } else {
                System.err.println("Falha ao editar a pergunta (Verifique se é sua ou se já tem respostas).");
            }

        } catch (java.time.format.DateTimeParseException e) {
            System.err.println("Formato de data inválido. Use dd-MM-yyyy HH:mm.");
        } catch (NumberFormatException e) {
            System.err.println("Número de opções inválido.");
        } catch (Exception e) {
            System.err.println("Ocorreu um erro inesperado: " + e.getMessage());
        }
    }

    /**
     * (Docente) Permite ao docente editar o seu próprio perfil
     * (ex: nome, password).
     */
    private void handleEditProfileDocente() {
        System.out.println("\n--- Editar Registo Pessoal (Docente) ---");
        try {
            Docente currentUser = (Docente) loggedInUser;

            // Pedir novos dados
            System.out.print("Novo Nome (Atual: " + currentUser.getNome() + "): ");
            String newName = scanner.nextLine();
            System.out.print("Novo Email (Atual: " + currentUser.getEmail() + "): ");
            String newEmail = scanner.nextLine();
            System.out.print("Nova Password (Deixe em branco para não alterar): ");
            String newPass1 = scanner.nextLine();
            String newPass2 = "";
            if (!newPass1.isEmpty()) {
                System.out.print("Confirme a Nova Password: ");
                newPass2 = scanner.nextLine();
                if (!newPass1.equals(newPass2)) {
                    System.err.println("As passwords não coincidem.");
                    return;
                }
            }

            // Preencher os dados
            // (Usamos "isEmpty" para que o utilizador possa manter o dado atual se carregar Enter)
            String finalName = newName.isEmpty() ? currentUser.getNome() : newName;
            String finalEmail = newEmail.isEmpty() ? currentUser.getEmail() : newEmail;

            // Criar o objeto Docente ATUALIZADO
            Docente updatedDocente = new Docente(currentUser.getId(), finalName, finalEmail);

            // 4. Chamar a Conexão
            if (connection.updateProfileDocente(updatedDocente, newPass1)) {
                System.out.println("Perfil atualizado com sucesso!");
                this.loggedInUser = updatedDocente; // Atualiza o objeto local
            } else {
                System.err.println("Falha ao atualizar o perfil. (O email pode já estar em uso)");
            }

        } catch (Exception e) {
            System.err.println("Erro inesperado: " + e.getMessage());
        }
    }


    /**
     * (Estudante) Permite ao estudante editar o seu próprio perfil
     * (ex: nome, password).
     */
    private void handleEditProfileEstudante() {
        System.out.println("\n--- Editar Registo Pessoal (Estudante) ---");
        try {
            Estudante currentUser = (Estudante) loggedInUser;

            // Pedir novos dados
            System.out.print("Novo Nome (Atual: " + currentUser.getNome() + "): ");
            String newName = scanner.nextLine();
            System.out.print("Novo Email (Atual: " + currentUser.getEmail() + "): ");
            String newEmail = scanner.nextLine();
            System.out.print("Novo Número (Atual: " + currentUser.getStudentNumber() + "): ");
            String newNumber = scanner.nextLine();
            System.out.print("Nova Password (Deixe em branco para não alterar): ");
            String newPass1 = scanner.nextLine();
            String newPass2 = "";
            if (!newPass1.isEmpty()) {
                System.out.print("Confirme a Nova Password: ");
                newPass2 = scanner.nextLine();
                if (!newPass1.equals(newPass2)) {
                    System.err.println("As passwords não coincidem.");
                    return;
                }
            }

            // Preencher os dados
            String finalName = newName.isEmpty() ? currentUser.getNome() : newName;
            String finalEmail = newEmail.isEmpty() ? currentUser.getEmail() : newEmail;
            String finalNumber = newNumber.isEmpty() ? currentUser.getStudentNumber() : newNumber;

            // Criar o objeto Estudante ATUALIZADO
            Estudante updatedEstudante = new Estudante(currentUser.getId(), finalName, finalEmail, finalNumber);

            // Chamar a Conexão
            if (connection.updateProfileEstudante(updatedEstudante, newPass1)) {
                System.out.println("Perfil atualizado com sucesso!");
                this.loggedInUser = updatedEstudante; // Atualiza o objeto local
            } else {
                System.err.println("Falha ao atualizar o perfil. (Email ou número podem já estar em uso)");
            }

        } catch (Exception e) {
            System.err.println("Erro inesperado: " + e.getMessage());
        }
    }

    /**
     * Pede e exibe o histórico de respostas do estudante.
     */
    private void handleViewMyAnswers() {
        System.out.println("\n--- O Meu Histórico de Respostas (Perguntas Expiradas) ---");

        List<SubmittedAnswer> answers = connection.getMyAnswers();

        if (answers == null) {
            System.err.println("Erro ao obter o histórico do servidor.");
            return;
        }
        if (answers.isEmpty()) {
            System.out.println("Ainda não respondeu a nenhuma pergunta (ou as perguntas a que respondeu ainda não expiraram).");
            return;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (SubmittedAnswer ans : answers) {
            System.out.println("---------------------------------");
            System.out.println(" Pergunta: " + ans.getQuestionEnunciado());
            System.out.println(" A sua Resposta: " + ans.getMyAnswer().toUpperCase());
            System.out.println(" Resposta Certa: " + ans.getCorrectAnswer().toUpperCase());
            System.out.println(" Resultado: " + (ans.isCorrect() ? "-->CORRETA" : "-->ERRADA"));
            System.out.println(" Submetida em: " + ans.getSubmissionTime().format(formatter));
        }
        System.out.println("---------------------------------");
    }

    /**
     * Pede o código de uma pergunta e exibe os resultados e estatísticas.
     * Oferece a opção de exportar para CSV.
     */
    private void handleViewQuestionResults() {
        System.out.println("\n--- Ver Resultados e Exportar CSV ---");
        System.out.print("Insira o CÓDIGO DE ACESSO: ");
        String accessCode = scanner.nextLine().trim().toUpperCase();
        if (accessCode.isEmpty()) return;

        // Chamada ÚNICA ao servidor
        ServerConnection.QuestionFullReport report = connection.getQuestionResults(accessCode);

        if (report == null) {
            System.err.println("Erro: Pergunta não encontrada ou sem permissão.");
            return;
        }

        Question q = report.question;
        List<QuestionResult> list = report.results;

        // Mostrar Resumo
        System.out.println("\n--- Dados da Pergunta ---");
        System.out.println("Enunciado: " + q.getEnunciado());
        System.out.println("Opção Correta: " + q.getCorrectAnswer().toUpperCase());
        System.out.println("Total de Respostas: " + list.size());

        // Tabela Simples na Consola
        System.out.printf("\n%-10s | %-20s | %s\n", "Número", "Aluno", "Resposta");
        for(QuestionResult res : list) {
            System.out.printf("%-10s | %-20s | %s\n",
                    res.getStudentNumber(), res.getStudentName(), res.getAnswerGiven().toUpperCase());
        }

        // Gerar CSV
        System.out.print("\nGerar CSV? (s/n): ");
        if (scanner.nextLine().trim().equalsIgnoreCase("s")) {
            generateCSV(q, list);
        }
    }


    private void generateCSV(Question q, List<QuestionResult> results) {

        String fileName = "resultados_" + q.getAccessCode() + ".csv";

        // Usa "ISO_LOCAL_DATE" ou padrão costumizado para formatar as datas
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            // Cabeçalho da Pergunta
            writer.println("\"dia\";\"hora inicial\";\"hora final\";\"enunciado da pergunta\";\"opção certa\"");

            writer.printf("\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"\n",
                    q.getBeginDateHour().format(dateFmt),       // Dia
                    q.getBeginDateHour().format(timeFmt),       // Hora Inicio
                    q.getEndDateHour().format(timeFmt),         // Hora Fim
                    q.getEnunciado(),
                    q.getCorrectAnswer()
            );

            writer.println();

            // Opções
            writer.println("\"opção\";\"texto da opção\"");
            for (Option op : q.getOptions()) {
                writer.printf("\"%s\";\"%s\"\n", op.getLetter(), op.getTextOption());
            }

            writer.println();

            // Respostas dos Alunos
            writer.println("\"número de estudante\";\"nome\";\"e-mail\";\"resposta\"");

            for (QuestionResult res : results) {
                writer.printf("\"%s\";\"%s\";\"%s\";\"%s\"\n",
                        res.getStudentNumber(),
                        res.getStudentName(),
                        res.getStudentEmail(),
                        res.getAnswerGiven()
                );
            }

            System.out.println("Ficheiro exportado com sucesso: " + fileName);

        } catch (Exception e) {
            System.err.println("Erro ao criar ficheiro CSV: " + e.getMessage());
        }
    }

}