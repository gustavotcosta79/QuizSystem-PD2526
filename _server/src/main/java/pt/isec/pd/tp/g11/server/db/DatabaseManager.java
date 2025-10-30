/*
 * Ficheiro: DatabaseManager.java
 * Objetivo: Classe responsável por toda a interação com a base de dados SQLite.
 * Responsabilidade: Conectar à BD, criar tabelas se não existirem,
 * executar queries (SELECT, INSERT, UPDATE, DELETE).
 */
package pt.isec.pd.tp.g11.server.db;

import pt.isec.pd.tp.g11.common.model.Docente;
import pt.isec.pd.tp.g11.common.model.Estudante;
import pt.isec.pd.tp.g11.common.model.User;
import pt.isec.pd.tp.g11.server.utils.SecurityUtils;

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
                    "  password TEXT NOT NULL" +    // TODO: Usar HASH para passwords
                    ");";
            stmt.execute(sqlDocente);

            // Tabela Estudante
            String sqlEstudante = "CREATE TABLE IF NOT EXISTS Estudante (" +
                    "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "  numero TEXT NOT NULL UNIQUE," +  //
                    "  nome TEXT NOT NULL," +
                    "  email TEXT NOT NULL UNIQUE," +  //
                    "  password TEXT NOT NULL" +   // TODO: Usar HASH para passwords
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

    // TODO: Implementar registerDocente(Docente docente, String passwordHash, String codigoRegisto)
    // TODO: Implementar getDocenteRegisterHash() (para ler o hash da tabela Configuracao)

    // --- Métodos Futuros ---
    // public int getDbVersion() { ... }
    // public void incrementDbVersion() { ... }
    // etc...

}