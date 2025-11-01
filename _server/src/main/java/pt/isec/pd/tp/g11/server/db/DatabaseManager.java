/*
 * Ficheiro: DatabaseManager.java
 * Objetivo: Classe responsável por toda a interação com a base de dados SQLite.
 * Responsabilidade: Conectar à BD, criar tabelas se não existirem,
 * executar queries (SELECT, INSERT, UPDATE, DELETE).
 */
package pt.isec.pd.tp.g11.server.db;

import pt.isec.pd.tp.g11.common.model.*;
import pt.isec.pd.tp.g11.server.utils.SecurityUtils;
import java.sql.Timestamp;
import java.sql.ResultSet;

import java.io.File;
import java.sql.*;

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

            // Define o caminho completo para o ficheiro da BD
            // TODO: Implementar nomeação de ficheiros para backups
            // Por agora, vamos usar um nome fixo.
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
        // Usar try-with-resources para garantir que o Statement fecha
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

                // 2. ALTERAÇÃO NA LÓGICA: Verificar o hash
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
        // 3. ALTERAÇÃO NA QUERY: Selecionar a password, procurar SÓ por email
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

                // 4. ALTERAÇÃO NA LÓGICA: Verificar o hash
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


    /**
     * Tenta registar um novo estudante na base de dados.
     * Falha se o email ou o número de estudante já existirem.
     * @param estudante O objeto Estudante (sem ID)
     * @param passwordHash O hash da password
     * @return true se o registo for bem-sucedido, false caso contrário (ex: dados duplicados)
     */
    public boolean registerEstudante(Estudante estudante, String passwordHash) {
        if (connection == null) return false;

        // O enunciado exige que email e número sejam únicos.
        // A nossa BD já tem "UNIQUE" nesses campos, por isso
        // uma tentativa de inserir duplicados vai falhar com uma SQLException.
        String sql = "INSERT INTO Estudante(numero, nome, email, password) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, estudante.getStudentNumber());
            pstmt.setString(2, estudante.getNome());
            pstmt.setString(3, estudante.getEmail());
            pstmt.setString(4, passwordHash);

            pstmt.executeUpdate(); // Executa o INSERT

            System.out.println("[DBManager] Novo estudante registado: " + estudante.getEmail());
            return true; // Sucesso

        } catch (SQLException e) {
            // "SQLITE_CONSTRAINT_UNIQUE" (código 19) indica falha na restrição UNIQUE
            if (e.getErrorCode() == 19) {
                System.err.println("[DBManager] Falha no registo: Email ou Número de Estudante já existe. " + estudante.getEmail());
            } else {
                System.err.println("[DBManager] Erro ao registar estudante: " + e.getMessage());
            }
            return false; // Falha
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
    public int registerDocente(Docente docente, String passwordHash, String codigoRegistoFornecido) {
        if (connection == null) return 2; // Erro genérico

        // 1. Verificar o código de registo de docente
        String hashCodigoCorreto = getDocenteRegisterHash();
        if (hashCodigoCorreto == null) {
            System.err.println("[DBManager] Falha no registo: Não foi possível obter o hash do código da BD.");
            return 2;
        }

        // Usamos a mesma função 'checkPassword' para verificar o código
        if (!SecurityUtils.checkPassword(codigoRegistoFornecido, hashCodigoCorreto)) {
            System.err.println("[DBManager] Falha no registo de docente: Código de registo errado.");
            return 1; // Código errado
        }

        // 2. Se o código estiver correto, inserir o docente
        String sql = "INSERT INTO Docente(nome, email, password) VALUES (?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, docente.getNome());
            pstmt.setString(2, docente.getEmail());
            pstmt.setString(3, passwordHash);
            pstmt.executeUpdate();

            System.out.println("[DBManager] Novo docente registado: " + docente.getEmail());
            return 0; // Sucesso

        } catch (SQLException e) {
            // "SQLITE_CONSTRAINT_UNIQUE" (código 19) indica email duplicado
            if (e.getErrorCode() == 19) {
                System.err.println("[DBManager] Falha no registo de docente: Email já existe. " + docente.getEmail());
            } else {
                System.err.println("[DBManager] Erro ao registar docente: " + e.getMessage());
            }
            return 2; // Email duplicado ou outro erro de BD
        }
    }

    // ... (depois do teu método registerDocente)

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
    public String createQuestion(Question question, int idDocente) {
        if (connection == null) return null;

        String sqlInsertQuestion = "INSERT INTO Pergunta(idDocente, codigoAcesso, enunciado, dataHoraInicio, dataHoraFim, respostaCerta) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        String sqlInsertOption = "INSERT INTO Opcao(idPergunta, letra, textoOpcao) VALUES (?, ?, ?)";

        String accessCode = generateAccessCode();

        try {
            // --- Iniciar Transação ---
            connection.setAutoCommit(false);

            int idPergunta; // O ID da pergunta que vamos inserir

            // 1. Inserir a Pergunta
            try (PreparedStatement pstmtQuestion = connection.prepareStatement(sqlInsertQuestion, Statement.RETURN_GENERATED_KEYS)) {
                pstmtQuestion.setInt(1, idDocente);
                pstmtQuestion.setString(2, accessCode);
                pstmtQuestion.setString(3, question.getEnunciado());
                // Converter LocalDateTime para Timestamp SQL
                pstmtQuestion.setTimestamp(4, Timestamp.valueOf(question.getBeginDateHour()));
                pstmtQuestion.setTimestamp(5, Timestamp.valueOf(question.getEndDateHour()));
                pstmtQuestion.setString(6, question.getCorrectAnswer());

                pstmtQuestion.executeUpdate();

                // Obter o ID da pergunta que acabámos de inserir
                try (ResultSet generatedKeys = pstmtQuestion.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        idPergunta = generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Falha ao obter ID da pergunta, nenhum ID foi gerado.");
                    }
                }
            }

            // 2. Inserir as Opções
            try (PreparedStatement pstmtOption = connection.prepareStatement(sqlInsertOption)) {
                for (Option option : question.getOptions()) {
                    pstmtOption.setInt(1, idPergunta);
                    pstmtOption.setString(2, option.getLetter());
                    pstmtOption.setString(3, option.getTextOption());
                    pstmtOption.addBatch(); // Adicionar à "fila" de inserts
                }
                pstmtOption.executeBatch(); // Executar todos os inserts de opções de uma vez
            }

            // 3. Se tudo correu bem, confirmar a transação
            connection.commit();

            // TODO: Enviar um heartbeat com SQL (aqui!)
            //

            return accessCode;

        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao criar pergunta. A fazer rollback. " + e.getMessage());
            // 4. Se algo falhou, reverter tudo
            try {
                connection.rollback();
            } catch (SQLException ex) {
                System.err.println("[DBManager] Erro crítico ao fazer rollback: " + ex.getMessage());
            }
            return null; // Falha
        } finally {
            // Garantir que voltamos ao modo normal
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) { /* ignorar */ }
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
    public boolean submitAnswer(int idEstudante, int idPergunta, String respostaLetra) {
        if (connection == null) return false;

        // A tabela 'Resposta' foi definida com UNIQUE(idEstudante, idPergunta)
        //
        String sql = "INSERT INTO Resposta(idEstudante, idPergunta, respostaSubmetida) VALUES (?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, idEstudante);
            pstmt.setInt(2, idPergunta);
            pstmt.setString(3, respostaLetra);
            pstmt.executeUpdate();

            System.out.println("[DBManager] Resposta do Estudante " + idEstudante + " à Pergunta " + idPergunta + " registada.");

            // TODO: Aqui é um ponto crítico onde tens de enviar um heartbeat
            // com a query SQL para os backups se manterem sincronizados.
            //

            return true;

        } catch (SQLException e) {
            // "SQLITE_CONSTRAINT_UNIQUE" (código 19) indica falha na restrição UNIQUE
            if (e.getErrorCode() == 19) {
                System.err.println("[DBManager] Estudante " + idEstudante + " já respondeu à pergunta " + idPergunta + ".");
            } else {
                System.err.println("[DBManager] Erro ao submeter resposta: " + e.getMessage());
            }
            return false; // Falha
        }
    }

}

    // --- Métodos Futuros ---
    // public int getDbVersion() { ... }
    // public void incrementDbVersion() { ... }
    // etc...