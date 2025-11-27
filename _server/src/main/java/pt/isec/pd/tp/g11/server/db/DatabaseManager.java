/*
 * Ficheiro: DatabaseManager.java
 * Objetivo: Classe responsável por toda a interação com a base de dados SQLite.
 * Responsabilidade: Conectar à BD, criar tabelas se não existirem,
 * executar queries (SELECT, INSERT, UPDATE, DELETE).
 */
package pt.isec.pd.tp.g11.server.db;

import pt.isec.pd.tp.g11.common.model.*;
import pt.isec.pd.tp.g11.server.utils.SecurityUtils;

import java.sql.ResultSet;
import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private Connection connection = null;
    private final String dbFolderPath; // Pasta onde o ficheiro .db será guardado
    private String dbFilePath = null;  // Caminho completo para o ficheiro .db

    public DatabaseManager(String dbFolderPath) {
        this.dbFolderPath = dbFolderPath;
    }

    /**
     * Estabelece a ligação à base de dados SQLite.
     * Cria o ficheiro e as tabelas se não existirem.
     * * @return true se a ligação for bem sucedida, false caso contrário.
     */
    public boolean connect() {
        try {
            // Garante que a pasta existe
            File folder = new File(dbFolderPath);
            if (!folder.exists()) {
                folder.mkdirs(); // Cria a pasta se não existir
            }

            this.dbFilePath = dbFolderPath + File.separator + "pd_database.db";

            // Construir a string de conexão JDBC para SQLite
            String url = "jdbc:sqlite:" + this.dbFilePath;

            // Estabelecer a conexão
            connection = DriverManager.getConnection(url);
            System.out.println("[DBManager] Ligação a SQLite estabelecida com sucesso: " + dbFilePath);

            // Criar as tabelas se a base de dados for nova
            createTablesIfNotExists();
            insertInitialConfigAndTestData(); // Insere dados iniciais se necessário

            return true;

        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao ligar à base de dados: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fecha a ligação à base de dados.
     */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DBManager] Ligação SQLite fechada.");
            }
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao fechar a ligação: " + e.getMessage());
        }
    }

    /**
     * Cria as tabelas necessárias na base de dados se elas ainda não existirem.
     * Baseado na Figura 3 do enunciado.
     */
    private void createTablesIfNotExists() {

        try (Statement stmt = connection.createStatement()) {

            // Tabela Configuracao
            String sqlConfiguracao = "CREATE TABLE IF NOT EXISTS Configuracao (" +
                    "  dbVersion INTEGER PRIMARY KEY DEFAULT 0," + //
                    "  codigoRegistoDocente TEXT NOT NULL" +       //
                    ");";
            stmt.execute(sqlConfiguracao);
            // TODO: Inserir a versão inicial e o hash do código se a tabela foi criada agora

            // Tabela Docente
            String sqlDocente = "CREATE TABLE IF NOT EXISTS Docente (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  nome TEXT NOT NULL," +
                    "  email TEXT NOT NULL UNIQUE," + //
                    "  password TEXT NOT NULL" +    //
                    ");";
            stmt.execute(sqlDocente);

            // Tabela Estudante
            String sqlEstudante = "CREATE TABLE IF NOT EXISTS Estudante (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  numero TEXT NOT NULL UNIQUE," +  //
                    "  nome TEXT NOT NULL," +
                    "  email TEXT NOT NULL UNIQUE," +  //
                    "  password TEXT NOT NULL" +   //
                    ");";
            stmt.execute(sqlEstudante);

            // Tabela Pergunta
            String sqlPergunta = "CREATE TABLE IF NOT EXISTS Pergunta (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  idDocente INTEGER NOT NULL," +
                    "  codigoAcesso TEXT NOT NULL UNIQUE," + //
                    "  enunciado TEXT NOT NULL," +
                    "  dataHoraInicio DATETIME NOT NULL," + //
                    "  dataHoraFim DATETIME NOT NULL," +    //
                    "  respostaCerta TEXT NOT NULL," + // Guardar 'a', 'b', etc.
                    "  FOREIGN KEY (idDocente) REFERENCES Docente(id)" +
                    ");";
            stmt.execute(sqlPergunta);

            // Tabela Opcao
            String sqlOpcao = "CREATE TABLE IF NOT EXISTS Opcao (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  idPergunta INTEGER NOT NULL," +
                    "  letra TEXT NOT NULL," + // 'a', 'b', 'c', ...
                    "  textoOpcao TEXT NOT NULL," +
                    "  FOREIGN KEY (idPergunta) REFERENCES Pergunta(id) ON DELETE CASCADE," + // Se apagar pergunta, apaga opções
                    "  UNIQUE (idPergunta, letra)" + // Não pode haver duas opções 'a' na mesma pergunta
                    ");";
            stmt.execute(sqlOpcao);

            // Tabela Resposta (relação entre Estudante e Pergunta)
            String sqlResposta = "CREATE TABLE IF NOT EXISTS Resposta (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  idEstudante INTEGER NOT NULL," +
                    "  idPergunta INTEGER NOT NULL," +
                    "  respostaSubmetida TEXT NOT NULL," + // 'a', 'b', etc.
                    "  dataHoraSubmissao DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "  FOREIGN KEY (idEstudante) REFERENCES Estudante(id)," +
                    "  FOREIGN KEY (idPergunta) REFERENCES Pergunta(id)," +
                    "  UNIQUE (idEstudante, idPergunta)" + // Estudante só responde uma vez
                    ");";
            stmt.execute(sqlResposta);

            System.out.println("[DBManager] Tabelas verificadas/criadas com sucesso.");

        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao criar/verificar tabelas: " + e.getMessage());
        }
    }

    /**
     * Insere a configuração inicial (versão 0, código docente) e
     * dados de teste (um docente, um estudante) se a tabela
     * Configuracao estiver vazia.
     */

    private void insertInitialConfigAndTestData() {
        String checkSql = "SELECT COUNT(*) FROM Configuracao";
        String insertConfigSql = "INSERT INTO Configuracao(dbVersion, codigoRegistoDocente) VALUES (?, ?)";
        String insertDocenteSql = "INSERT INTO Docente(nome, email, password) VALUES (?, ?, ?)";
        String insertEstudanteSql = "INSERT INTO Estudante(numero, nome, email, password) VALUES (?, ?, ?, ?)";

        try (Statement checkStmt = connection.createStatement();
             ResultSet rs = checkStmt.executeQuery(checkSql)) {

            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("[DBManager] Base de dados vazia. A inserir dados iniciais e hashes...");
                connection.setAutoCommit(false);

                try (PreparedStatement configStmt = connection.prepareStatement(insertConfigSql);
                     PreparedStatement docenteStmt = connection.prepareStatement(insertDocenteSql);
                     PreparedStatement estudanteStmt = connection.prepareStatement(insertEstudanteSql)) {

                    // 1. Inserir Configuração Inicial (com hash)
                    configStmt.setInt(1, 0);
                    // 2. ALTERAÇÃO AQUI
                    configStmt.setString(2, SecurityUtils.hashPassword("CODIGO_DOCENTE_123")); //
                    configStmt.executeUpdate();

                    // 2. Inserir Docente de Teste (com hash)
                    docenteStmt.setString(1, "Docente Teste");
                    docenteStmt.setString(2, "docente@isec.pt");
                    // 3. ALTERAÇÃO AQUI
                    docenteStmt.setString(3, SecurityUtils.hashPassword("1234")); // Guarda o hash, não "1234"
                    docenteStmt.executeUpdate();

                    // 3. Inserir Estudante de Teste (com hash)
                    estudanteStmt.setString(1, "123456");
                    estudanteStmt.setString(2, "Estudante Teste");
                    estudanteStmt.setString(3, "aluno@isec.pt");
                    // 4. ALTERAÇÃO AQUI
                    estudanteStmt.setString(4, SecurityUtils.hashPassword("senha")); // Guarda o hash, não "senha"
                    estudanteStmt.executeUpdate();

                    connection.commit();
                    System.out.println("[DBManager] Hashes e dados de teste inseridos com sucesso.");

                } catch (SQLException eInsert) {
                    connection.rollback();
                    System.err.println("[DBManager] Erro ao inserir dados (hash): " + eInsert.getMessage());
                } finally {
                    connection.setAutoCommit(true);
                }
            } else {
                System.out.println("[DBManager] Base de dados já contém dados. Dados de teste não inseridos.");
            }
        } catch (SQLException eCheck) {
            System.err.println("[DBManager] Erro ao verificar dados iniciais: " + eCheck.getMessage());
        }
    }

    // --- Métodos Futuros ---

    /**
     * Verifica as credenciais de login na base de dados USANDO HASHES.
     * @param email O email fornecido.
     * @param plainPassword A password EM TEXTO SIMPLES fornecida pelo utilizador.
     * @return Um objeto User (Docente ou Estudante) se o login for válido, null caso contrário.
     */
    public User checkLogin(String email, String plainPassword) {
        if (connection == null) {
            System.err.println("[DBManager] Tentativa de login sem ligação ativa.");
            return null;
        }

        // Tentar encontrar na tabela Docente
        // 1. ALTERAÇÃO NA QUERY: Selecionar a password, procurar SÓ por email
        String sqlDocente = "SELECT id, nome, password FROM Docente WHERE email = ?";
        try (PreparedStatement pstmtDocente = connection.prepareStatement(sqlDocente)) {

            pstmtDocente.setString(1, email);
            ResultSet rsDocente = pstmtDocente.executeQuery();

            if (rsDocente.next()) {
                // Encontrado Docente. Agora verificar a password.
                int id = rsDocente.getInt("id");
                String nome = rsDocente.getString("nome");
                String storedHash = rsDocente.getString("password"); // O hash guardado na BD

                // 2. Verificar o hash
                if (SecurityUtils.checkPassword(plainPassword, storedHash)) {
                    System.out.println("[DBManager] Login HASH bem-sucedido (Docente): " + email);
                    return new Docente(id, nome, email);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao verificar login Docente (hash): " + e.getMessage());
            return null;
        }

        // Se não encontrou (ou a password falhou), tentar na tabela Estudante
        // 3.Selecionar a password, procurar SÓ por email
        String sqlEstudante = "SELECT id, nome, numero, password FROM Estudante WHERE email = ?";
        try (PreparedStatement pstmtEstudante = connection.prepareStatement(sqlEstudante)) {

            pstmtEstudante.setString(1, email);
            ResultSet rsEstudante = pstmtEstudante.executeQuery();

            if (rsEstudante.next()) {
                // Encontrado Estudante. Agora verificar a password.
                int id = rsEstudante.getInt("id");
                String nome = rsEstudante.getString("nome");
                String numero = rsEstudante.getString("numero");
                String storedHash = rsEstudante.getString("password"); // O hash guardado na BD

                // 4.  Verificar o hash
                if (SecurityUtils.checkPassword(plainPassword, storedHash)) {
                    System.out.println("[DBManager] Login HASH bem-sucedido (Estudante): " + email);
                    return new Estudante(id, nome, email, numero); // Confirma a ordem dos parâmetros!
                }
            }
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao verificar login Estudante (hash): " + e.getMessage());
            return null;
        }

        // Se não encontrou ou a password falhou
        System.out.println("[DBManager] Login HASH falhou (não encontrado ou pass errada): " + email);
        return null;
    }


    // Retorna a String SQL em caso de sucesso, ou NULL em caso de erro
    public String registerEstudante(Estudante estudante, String passwordHash) {
        if (connection == null) return null;

        // Construímos a query manualmente para poder enviar aos backups
        String sql = String.format("INSERT INTO Estudante(numero, nome, email, password) VALUES ('%s', '%s', '%s', '%s')",
                estudante.getStudentNumber(), estudante.getNome(), estudante.getEmail(), passwordHash);

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            incrementDbVersion(); // Incrementa a versão local
            System.out.println("[DBManager] Novo estudante registado (v" + getDbVersion() + "): " + estudante.getEmail());
            return sql; // Retorna a query para ser enviada via Multicast
        } catch (SQLException e) {
            if (e.getErrorCode() == 19) System.err.println("Estudante duplicado.");
            else System.err.println("Erro BD: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtém o hash do código de registo de docente guardado na tabela de Configuração.
     * @return O hash do código, ou null se não for encontrado.
     */
    private String getDocenteRegisterHash() {
        if (connection == null) return null;

        String sql = "SELECT codigoRegistoDocente FROM Configuracao LIMIT 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getString("codigoRegistoDocente");
            }
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao ler o código de registo de docente: " + e.getMessage());
        }
        return null;
    }

    /**
     * Tenta registar um novo docente na base de dados.
     * Falha se o código de registo estiver errado ou se o email já existir.
     * @param docente O objeto Docente (sem ID)
     * @param passwordHash O hash da password
     * @param codigoRegistoFornecido O código de registo (em texto simples) fornecido
     * @return 0 (Sucesso), 1 (Falha - Código errado), 2 (Falha - Email duplicado/Erro)
     */
    public String registerDocente(Docente docente, String passwordHash, String codigoRegistoFornecido) {
        if (connection == null) return null;

        // 1. Verificar o código de registo de docente
        String hashCodigoCorreto = getDocenteRegisterHash();
        if (hashCodigoCorreto == null) {
            System.err.println("[DBManager] Falha no registo: Não foi possível obter o hash do código da BD.");
            return null;
        }

        // Usamos a mesma função 'checkPassword' para verificar o código
        if (!SecurityUtils.checkPassword(codigoRegistoFornecido, hashCodigoCorreto)) {
            System.err.println("[DBManager] Falha no registo de docente: Código de registo errado.");
            return "WRONG_CODE"; // Código errado
        }
        String sql = String.format("INSERT INTO Docente(nome, email, password) VALUES ('%s', '%s', '%s')",
                docente.getNome(),
                docente.getEmail(),
                passwordHash);
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            incrementDbVersion(); // Incrementa a versão da BD

            System.out.println("[DBManager] Novo docente registado (v" + getDbVersion() + "): " + docente.getEmail());
            return sql; // <--- SUCESSO: Retorna a query

        } catch (SQLException e) {
            if (e.getErrorCode() == 19) { // Código SQLite para UNIQUE constraint failed
                System.err.println("[DBManager] Falha: Email já existe. " + docente.getEmail());
            } else {
                System.err.println("[DBManager] Erro SQL: " + e.getMessage());
            }
            return null; // Falha na inserção
        }

    }

    /**
     * Gera um código de acesso aleatório e único.
     * (Podes tornar esta lógica mais robusta mais tarde)
     * @return Um código de 6 caracteres alfanuméricos.
     */
    private String generateAccessCode() {
        // TODO: Verificar se o código já existe na BD
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        java.util.Random rnd = new java.util.Random();
        while (code.length() < 6) {
            int index = (int) (rnd.nextFloat() * chars.length());
            code.append(chars.charAt(index));
        }
        return code.toString();
    }

    /**
     * Insere uma nova pergunta e as suas opções na BD.
     * Usa uma transação para garantir que ou tudo ou nada é inserido.
     *
     * @param question O objeto Pergunta (com a lista de Opções)
     * @param idDocente O ID do docente autenticado
     * @return O código de acesso gerado, ou null se falhar.
     */
    public String[] createQuestion(Question question, int idDocente) {
        if (connection == null) return null;

        String accessCode = generateAccessCode(); // O teu método existente
        StringBuilder sqlParaBackup = new StringBuilder(); // Aqui vamos construir a string gigante

        // Vamos envolver tudo numa transação para o Backup
        sqlParaBackup.append("BEGIN TRANSACTION;");

        try {
            connection.setAutoCommit(false); // Transação Local

            // 1. Inserir a Pergunta (Localmente)
            // Nota: Usamos PreparedStatement aqui para obter as chaves geradas com segurança
            String sqlInsertPergunta = "INSERT INTO Pergunta(idDocente, codigoAcesso, enunciado, dataHoraInicio, dataHoraFim, respostaCerta) VALUES (?, ?, ?, ?, ?, ?)";

            int idPerguntaGerado = -1;

            try (java.sql.PreparedStatement pstmt = connection.prepareStatement(sqlInsertPergunta, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, idDocente);
                pstmt.setString(2, accessCode);
                pstmt.setString(3, question.getEnunciado());
                pstmt.setString(4, question.getBeginDateHour().toString());
                pstmt.setString(5, question.getEndDateHour().toString());
                pstmt.setString(6, question.getCorrectAnswer());

                pstmt.executeUpdate();

                // Obter o ID que foi gerado
                try (java.sql.ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) idPerguntaGerado = rs.getInt(1);
                }
            }

            if (idPerguntaGerado == -1) throw new java.sql.SQLException("Falha ao obter ID da pergunta.");

            // --- CONSTRUIR SQL DA PERGUNTA PARA O BACKUP ---
            // Aqui escrevemos os valores literais na string
            String sqlPerguntaBackup = String.format("INSERT INTO Pergunta(idDocente, codigoAcesso, enunciado, dataHoraInicio, dataHoraFim, respostaCerta) VALUES (%d, '%s', '%s', '%s', '%s', '%s');",
                    idDocente, accessCode, question.getEnunciado(),
                    question.getBeginDateHour().toString(), question.getEndDateHour().toString(),
                    question.getCorrectAnswer());

            sqlParaBackup.append(sqlPerguntaBackup);

            // 2. Inserir as Opções
            String sqlInsertOpcao = "INSERT INTO Opcao(idPergunta, letra, textoOpcao) VALUES (?, ?, ?)";

            try (java.sql.PreparedStatement pstmtOp = connection.prepareStatement(sqlInsertOpcao)) {
                for (Option option : question.getOptions()) {
                    // Inserção Local
                    pstmtOp.setInt(1, idPerguntaGerado);
                    pstmtOp.setString(2, option.getLetter());
                    pstmtOp.setString(3, option.getTextOption());
                    pstmtOp.executeUpdate();

                    // --- CONSTRUIR SQL DA OPÇÃO PARA O BACKUP ---
                    // IMPORTANTE: Usamos o 'idPerguntaGerado' explicitamente!
                    String sqlOpcaoBackup = String.format("INSERT INTO Opcao(idPergunta, letra, textoOpcao) VALUES (%d, '%s', '%s');",
                            idPerguntaGerado, option.getLetter(), option.getTextOption());

                    sqlParaBackup.append(sqlOpcaoBackup);
                }
            }

            connection.commit(); // Confirmar Local
            incrementDbVersion(); // Atualizar versão

            // Fechar a transação na string do backup
            sqlParaBackup.append("COMMIT;");

            System.out.println("[DBManager] Pergunta criada (v" + getDbVersion() + "): " + accessCode);

            // Retornar [Código, SQL_Gigante]
            return new String[]{ accessCode, sqlParaBackup.toString() };

        } catch (Exception e) {
            try { connection.rollback(); } catch (java.sql.SQLException ex) {}
            System.err.println("[DBManager] Erro createQuestion: " + e.getMessage());
            return null;
        } finally {
            try { connection.setAutoCommit(true); } catch (java.sql.SQLException ex) {}
        }
    }


    /**
     * Insere a resposta de um estudante a uma pergunta.
     * Falha se o estudante já tiver respondido a essa pergunta.
     *
     * @param idEstudante ID do estudante (do authenticatedUser)
     * @param idPergunta ID da pergunta
     * @param respostaLetra A letra ('a', 'b', etc.) que o estudante submeteu
     * @return true se foi bem-sucedido, false caso contrário
     */
    public String submitAnswer(int idEstudante, int idPergunta, String respostaLetra) {
        if (connection == null) return null;


        String sql = String.format("INSERT INTO Resposta(idEstudante, idPergunta, respostaSubmetida) VALUES (%d, %d, '%s')",
                idEstudante, idPergunta, respostaLetra);

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            incrementDbVersion();
            System.out.println("[DBManager] Resposta registada (v" + getDbVersion() + ")");
            return sql;
        }catch (java.sql.SQLException e) {

            if (e.getErrorCode() == 19) {
                System.err.println("[DBManager] Estudante já respondeu a esta pergunta.");
            } else {
                System.err.println("[DBManager] Erro ao submeter resposta: " + e.getMessage());
            }
            return null;
        }


    }



    /**
     * Procura uma pergunta pelo código de acesso E verifica se está ativa.
     *
     * @param accessCode O código da pergunta.
     * @return O objeto Question (com a lista de Options) se estiver ativa, null caso contrário.
     */
    public Question getActiveQuestionByCode(String accessCode) {
        if (connection == null) return null;


                // Usamos strftime para forçar o formato ISO (com 'T') a corresponder
                // ao formato guardado pelo LocalDateTime.toString()
                String sqlQuestion = "SELECT * FROM Pergunta WHERE codigoAcesso = ? AND " +
                "strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime') BETWEEN dataHoraInicio AND dataHoraFim";


        String sqlOptions = "SELECT letra, textoOpcao FROM Opcao WHERE idPergunta = ?";

        Question question = null;

        java.util.List<Option> options = new java.util.ArrayList<>();

        try (PreparedStatement pstmtQuestion = connection.prepareStatement(sqlQuestion)) {
            pstmtQuestion.setString(1, accessCode);
            ResultSet rsQuestion = pstmtQuestion.executeQuery();

            if (rsQuestion.next()) {
                // Se encontrámos a pergunta, ela está ativa. Vamos preencher o objeto.
                int idPergunta = rsQuestion.getInt("id");
                String enunciado = rsQuestion.getString("enunciado");
                // Precisas de importar java.time.LocalDateTime
                String respostaCerta = rsQuestion.getString("respostaCerta");

                java.time.LocalDateTime inicio = java.time.LocalDateTime.parse(rsQuestion.getString("dataHoraInicio"));
                java.time.LocalDateTime fim = java.time.LocalDateTime.parse(rsQuestion.getString("dataHoraFim"));

                // Agora, ir buscar as opções
                try (PreparedStatement pstmtOptions = connection.prepareStatement(sqlOptions)) {
                    pstmtOptions.setInt(1, idPergunta);
                    ResultSet rsOptions = pstmtOptions.executeQuery();
                    while (rsOptions.next()) {
                        options.add(new Option(rsOptions.getString("letra"), rsOptions.getString("textoOpcao")));
                    }
                }

                // Criar o objeto Question para enviar ao cliente
                question = new Question(enunciado, inicio, fim, respostaCerta, options);
                question.setId(idPergunta);
                question.setAccessCode(accessCode);
                question.setIdDocente(rsQuestion.getInt("idDocente"));
            }
            // Se rsQuestion.next() for false, a pergunta não existe ou não está ativa
            // (porque o código está errado OU o período de tempo está errado)
            // e o método devolve null.

        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao procurar pergunta por código: " + e.getMessage());
        }
        return question;
    }

    /**
     * Obtém a lista de perguntas criadas por um docente específico.
     *
     * @param idDocente O ID do docente autenticado.
     * @param filter O filtro a aplicar ("ALL", "ACTIVE", "FUTURE", "PAST").
     * @return Uma lista de objetos Question (pode estar vazia).
     */
    public List<Question> getQuestionsByTeacher(int idDocente, String filter) {
        if (connection == null) return new ArrayList<>(); // se conexão com a base de dados não tiver sido bem sucedida
                                                        // Retorna lista vazia

        List<Question> questions = new ArrayList<>();

        // Começamos a query SQL básica
        String sql = "SELECT * FROM Pergunta WHERE idDocente = ?";

        // Adicionamos os filtros de tempo
        //String now = "datetime('now', 'localtime')"; BUGADO
        String now = "strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime')";
        switch (filter.toUpperCase()) {
            case "ACTIVE": // Ativas: now ESTÁ entre inicio e fim
                sql += " AND " + now + " BETWEEN dataHoraInicio AND dataHoraFim";
                break;
            case "FUTURE": // Futuras: now é ANTES do inicio
                sql += " AND " + now + " < dataHoraInicio";
                break;
            case "PAST": // Expiradas: now é DEPOIS do fim
                sql += " AND " + now + " > dataHoraFim";
                break;
            case "ALL":
            default:
                // Não adiciona filtro de tempo
                break;
        }
        sql += " ORDER BY dataHoraInicio DESC"; // Ordenar pelas mais recentes

        try (PreparedStatement pstmtQuestion = connection.prepareStatement(sql)) {
            pstmtQuestion.setInt(1, idDocente);
            ResultSet rsQuestion = pstmtQuestion.executeQuery();

            while (rsQuestion.next()) {
                // Para cada pergunta, preenchemos o objeto
                int idPergunta = rsQuestion.getInt("id");
                String enunciado = rsQuestion.getString("enunciado");
                LocalDateTime inicio = LocalDateTime.parse(rsQuestion.getString("dataHoraInicio"));
                LocalDateTime fim = LocalDateTime.parse(rsQuestion.getString("dataHoraFim"));
                String respostaCerta = rsQuestion.getString("respostaCerta");
                String codigoAcesso = rsQuestion.getString("codigoAcesso");

                // NOTA: Esta query NÃO carrega as Opções.
                // É mais eficiente carregar as opções só se o utilizador
                // quiser ver os detalhes de UMA pergunta.
                // Por agora, a lista de opções fica vazia.
                List<Option> options = new ArrayList<>();

                Question q = new Question(enunciado, inicio, fim, respostaCerta, options);
                q.setId(idPergunta);
                q.setIdDocente(idDocente);
                q.setAccessCode(codigoAcesso);

                questions.add(q);
            }

        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao listar perguntas do docente: " + e.getMessage());
        }
        return questions;
    }

    public String getDbFilePath() {
        return this.dbFilePath;
    }

    // Adicionar no final da classe DatabaseManager
    public int getDbVersion() {
        if (connection == null) return 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT dbVersion FROM Configuracao")) {
            if (rs.next()) return rs.getInt("dbVersion");
        } catch (SQLException e) { System.err.println("Erro ao ler versão: " + e.getMessage()); }
        return 0;
    }

    // Método privado para incrementar versão internamente
    private void incrementDbVersion() {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("UPDATE Configuracao SET dbVersion = dbVersion + 1");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    // Método para o BACKUP executar a query que vem do Primary
    public boolean executeReplicaQuery(String sql) {
        if (connection == null) return false;
        try (Statement stmt = connection.createStatement()) {
            System.out.println("[DBManager] A replicar query: " + sql);
            stmt.executeUpdate(sql);
            incrementDbVersion(); // O Backup também incrementa para ficar igual ao Primary
            return true;
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao replicar query: " + e.getMessage());
            return false;
        }
    }

    /**
     * Elimina uma pergunta, desde que não tenha respostas.
     * Retorna a query SQL (DELETE) para replicação, ou null se falhar.
     */
    public String deleteQuestion(String accessCode, int idDocente) {
        if (connection == null) return null;

        // 1. Query SQL para enviar ao Backup (é mais simples)
        String replicaSql = String.format("DELETE FROM Pergunta WHERE codigoAcesso = '%s'", accessCode);

        // 2. Query SQL para executar localmente (inclui todas as regras de negócio)
        String localDeleteSql = "DELETE FROM Pergunta " +
                "WHERE codigoAcesso = ? AND idDocente = ? " +
                "AND NOT EXISTS (SELECT 1 FROM Resposta WHERE Resposta.idPergunta = Pergunta.id)";

        try (PreparedStatement pstmt = connection.prepareStatement(localDeleteSql)) {
            pstmt.setString(1, accessCode);
            pstmt.setInt(2, idDocente);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                // Se 0 linhas foram afetadas, é porque uma das condições falhou:
                // 1. O código não existe.
                // 2. A pergunta não é deste docente.
                // 3. A pergunta JÁ TEM respostas.
                System.err.println("[DBManager] Falha ao eliminar: Pergunta não encontrada, não pertence ao docente, ou já tem respostas.");
                return null; // Falha
            }

            // Se chegou aqui (rowsAffected > 0), a eliminação foi bem-sucedida
            incrementDbVersion();
            System.out.println("[DBManager] Pergunta " + accessCode + " eliminada (v" + getDbVersion() + ")");
            return replicaSql; // Sucesso: retorna a query de replicação

        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao eliminar pergunta: " + e.getMessage());
            return null;
        }
    }

    /**
     * Edita uma pergunta existente, substituindo todos os seus dados e opções.
     * Falha se a pergunta não pertencer ao docente ou se já tiver respostas.
     * Retorna o bloco SQL (Transação) para replicação, ou null se falhar.
     */
    public String editQuestion(String accessCode, Question newQuestion, int idDocente) {
        if (connection == null) return null;

        StringBuilder replicaSql = new StringBuilder(); // String para o backup
        replicaSql.append("BEGIN TRANSACTION;");

        // 1. Query para encontrar o ID da pergunta
        String checkSql = "SELECT id FROM Pergunta WHERE codigoAcesso = ? AND idDocente = ? " +
                "AND NOT EXISTS (SELECT 1 FROM Resposta WHERE Resposta.idPergunta = Pergunta.id)";
        int idPergunta = -1;

        try {
            // --- Verificação ---
            try (PreparedStatement pstmtCheck = connection.prepareStatement(checkSql)) {
                pstmtCheck.setString(1, accessCode);
                pstmtCheck.setInt(2, idDocente);
                try (ResultSet rs = pstmtCheck.executeQuery()) {
                    if (!rs.next()) {
                        // Se não encontrou, é porque (código errado, não é dele, ou já tem respostas)
                        System.err.println("[DBManager] Falha ao editar: Pergunta não encontrada, não pertence ao docente, ou já tem respostas.");
                        return null;
                    }
                    idPergunta = rs.getInt("id");
                }
            }

            // --- Se a verificação passou, preparamos a transação local ---
            connection.setAutoCommit(false);

            // 2. SQL de UPDATE para a Pergunta
            String updatePerguntaSql = String.format("UPDATE Pergunta SET enunciado = '%s', dataHoraInicio = '%s', dataHoraFim = '%s', respostaCerta = '%s' WHERE id = %d;",
                    newQuestion.getEnunciado(), newQuestion.getBeginDateHour().toString(),
                    newQuestion.getEndDateHour().toString(), newQuestion.getCorrectAnswer(), idPergunta);

            replicaSql.append(updatePerguntaSql);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(updatePerguntaSql);
            }

            // 3. SQL de DELETE para as Opções ANTIGAS
            String deleteOpcoesSql = String.format("DELETE FROM Opcao WHERE idPergunta = %d;", idPergunta);
            replicaSql.append(deleteOpcoesSql);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(deleteOpcoesSql);
            }

            // 4. SQL de INSERT para as Opções NOVAS
            for (Option option : newQuestion.getOptions()) {
                String insertOpcaoSql = String.format("INSERT INTO Opcao(idPergunta, letra, textoOpcao) VALUES (%d, '%s', '%s');",
                        idPergunta, option.getLetter(), option.getTextOption());

                replicaSql.append(insertOpcaoSql);
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(insertOpcaoSql);
                }
            }

            // --- Fim da Transação ---
            replicaSql.append("COMMIT;");
            connection.commit(); // Confirma a transação local
            incrementDbVersion();

            System.out.println("[DBManager] Pergunta " + accessCode + " editada (v" + getDbVersion() + ")");
            return replicaSql.toString(); // Sucesso: retorna o bloco SQL

        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ex) {} // Desfaz em caso de erro
            System.err.println("[DBManager] Erro ao editar pergunta: " + e.getMessage());
            return null;
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ex) {} // Volta ao normal
        }
    }




    /**
     * Atualiza os dados de um Docente na BD.
     * @param docente O objeto Docente com os novos dados (o ID deve estar correto).
     * @param passwordHash O novo hash da password, ou null se a password não for alterada.
     * @return A query SQL para replicação, ou null em caso de falha (ex: email duplicado).
     */
    public String updateDocente(Docente docente, String passwordHash) {
        if (connection == null) return null;

        // 1. Construir a query SQL
        String sql = String.format("UPDATE Docente SET nome = '%s', email = '%s'",
                docente.getNome(),
                docente.getEmail()
        );

        // 2. Adicionar a password à query SÓ se foi fornecida uma nova
        if (passwordHash != null && !passwordHash.isEmpty()) {
            sql += String.format(", password = '%s'", passwordHash);
        }

        // 3. Adicionar a condição WHERE (MUITO IMPORTANTE)
        sql += String.format(" WHERE id = %d", docente.getId());

        // 4. Executar e replicar
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            incrementDbVersion();
            System.out.println("[DBManager] Docente atualizado (v" + getDbVersion() + "): " + docente.getEmail());
            return sql; // Sucesso: retorna a query para o multicast
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao atualizar docente: " + e.getMessage());
            return null; // Falha (ex: email duplicado)
        }
    }

    /**
     * Atualiza os dados de um Estudante na BD.
     * @param estudante O objeto Estudante com os novos dados (o ID deve estar correto).
     * @param passwordHash O novo hash da password, ou null se a password não for alterada.
     * @return A query SQL para replicação, ou null em caso de falha (ex: email/numero duplicado).
     */
    public String updateEstudante(Estudante estudante, String passwordHash) {
        if (connection == null) return null;

        // 1. Construir a query SQL
        String sql = String.format("UPDATE Estudante SET nome = '%s', email = '%s', numero = '%s'",
                estudante.getNome(),
                estudante.getEmail(),
                estudante.getStudentNumber()
        );

        // 2. Adicionar a password à query SÓ se foi fornecida uma nova
        if (passwordHash != null && !passwordHash.isEmpty()) {
            sql += String.format(", password = '%s'", passwordHash);
        }

        // 3. Adicionar a condição WHERE (MUITO IMPORTANTE)
        sql += String.format(" WHERE id = %d", estudante.getId());

        // 4. Executar e replicar
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            incrementDbVersion();
            System.out.println("[DBManager] Estudante atualizado (v" + getDbVersion() + "): " + estudante.getEmail());
            return sql; // Sucesso: retorna a query para o multicast
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao atualizar estudante: " + e.getMessage());
            return null; // Falha (ex: email/numero duplicado)
        }
    }

    /**
     * Obtém o histórico de respostas de um estudante para perguntas que já expiraram.
     */
    public List<SubmittedAnswer> getSubmittedAnswers(int idEstudante) {
        if (connection == null) return new ArrayList<>();

        List<SubmittedAnswer> answers = new ArrayList<>();

        // Query que junta Resposta e Pergunta
        String sql = "SELECT P.enunciado, R.respostaSubmetida, P.respostaCerta, R.dataHoraSubmissao " +
                "FROM Resposta R " +
                "JOIN Pergunta P ON R.idPergunta = P.id " +
                "WHERE R.idEstudante = ? " +
                "AND strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime') > P.dataHoraFim " + // Onde a pergunta já expirou
                "ORDER BY R.dataHoraSubmissao DESC"; // Mais recentes primeiro

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, idEstudante);

            DateTimeFormatter sqliteFormatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String enunciado = rs.getString("enunciado");
                    String yourAnswer = rs.getString("respostaSubmetida");
                    String correctAnswer = rs.getString("respostaCerta");
                    LocalDateTime time = LocalDateTime.parse(rs.getString("dataHoraSubmissao"),sqliteFormatter);

                    answers.add(new SubmittedAnswer(enunciado, yourAnswer, correctAnswer, time));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao obter histórico de respostas: " + e.getMessage());
        }
        return answers;
    }

    /**
     * Obtém a lista de todos os resultados (respostas de estudantes) para uma pergunta.
     * Apenas funciona se a pergunta pertence ao docente e já expirou.
     */
    public List<QuestionResult> getQuestionResults(String accessCode, int idDocente) {
        if (connection == null) return new ArrayList<>();

        List<QuestionResult> results = new ArrayList<>();

        // Query complexa que:
        // 1. Junta Resposta (R) e Estudante (E)
        // 2. Filtra pela Pergunta (P) correta usando o accessCode
        // 3. Verifica se a pergunta pertence ao docente (P.idDocente = ?)
        // 4. Verifica se a pergunta já expirou (now > P.dataHoraFim)
        String sql = "SELECT E.numero, E.nome, E.email, R.respostaSubmetida " +
                "FROM Resposta R " +
                "JOIN Estudante E ON R.idEstudante = E.id " +
                "JOIN Pergunta P ON R.idPergunta = P.id " +
                "WHERE P.codigoAcesso = ? AND P.idDocente = ? " +
                "AND strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime') > P.dataHoraFim " +
                "ORDER BY E.nome ASC"; // Ordena por nome de aluno

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, accessCode);
            pstmt.setInt(2, idDocente);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String numero = rs.getString("numero");
                    String nome = rs.getString("nome");
                    String email = rs.getString("email");
                    String resposta = rs.getString("respostaSubmetida");

                    results.add(new QuestionResult(numero, nome, email, resposta));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao obter resultados da pergunta: " + e.getMessage());
        }
        return results; // Retorna a lista (pode estar vazia se ninguém respondeu ou se a pergunta não é válida)
    }

    public Question getQuestionDetails(String accessCode) {
        if (connection == null) return null;

        String sqlQuestion = "SELECT * FROM Pergunta WHERE codigoAcesso = ?";
        String sqlOptions = "SELECT letra, textoOpcao FROM Opcao WHERE idPergunta = ?";

        Question question = null;
        List<Option> options = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sqlQuestion)) {
            pstmt.setString(1, accessCode);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int idPergunta = rs.getInt("id");
                // Preencher dados (incluindo datas e resposta certa para o CSV)
                String enunciado = rs.getString("enunciado");
                String respostaCerta = rs.getString("respostaCerta");
                LocalDateTime inicio = LocalDateTime.parse(rs.getString("dataHoraInicio"));
                LocalDateTime fim = LocalDateTime.parse(rs.getString("dataHoraFim"));

                // Buscar opções
                try (PreparedStatement pstmtOp = connection.prepareStatement(sqlOptions)) {
                    pstmtOp.setInt(1, idPergunta);
                    ResultSet rsOp = pstmtOp.executeQuery();
                    while (rsOp.next()) {
                        options.add(new Option(rsOp.getString("letra"), rsOp.getString("textoOpcao")));
                    }
                }

                question = new Question(enunciado, inicio, fim, respostaCerta, options);
                question.setId(idPergunta);
                question.setIdDocente(rs.getInt("idDocente"));
                question.setAccessCode(accessCode);
            }
        } catch (SQLException e) {
            System.err.println("[DB] Erro getQuestionDetails: " + e.getMessage());
        }
        return question;
    }

}
