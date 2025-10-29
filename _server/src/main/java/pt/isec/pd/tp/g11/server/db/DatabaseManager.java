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

            // Verificar se a tabela Configuracao já tem dados
            if (rs.next() && rs.getInt(1) == 0) {
                System.out.println("[DBManager] Base de dados vazia. A inserir dados iniciais e de teste...");

                // Desativar auto-commit para fazer tudo numa transação
                connection.setAutoCommit(false);

                try (PreparedStatement configStmt = connection.prepareStatement(insertConfigSql);
                     PreparedStatement docenteStmt = connection.prepareStatement(insertDocenteSql);
                     PreparedStatement estudanteStmt = connection.prepareStatement(insertEstudanteSql)) {

                    // 1. Inserir Configuração Inicial
                    configStmt.setInt(1, 0); // Versão inicial da BD
                    // TODO: Gerar e guardar um HASH seguro para o código, não o código em si!
                    configStmt.setString(2, "CODIGO_DOCENTE_123"); // Placeholder para o código
                    configStmt.executeUpdate();

                    // 2. Inserir Docente de Teste
                    docenteStmt.setString(1, "Docente Teste");
                    docenteStmt.setString(2, "docente@isec.pt"); // Email para testar o login
                    docenteStmt.setString(3, "1234");           // Password para testar o login (NÃO FAZER ISTO EM PRODUÇÃO!)
                    docenteStmt.executeUpdate();

                    // 3. Inserir Estudante de Teste
                    estudanteStmt.setString(1, "123456");            // Número de estudante
                    estudanteStmt.setString(2, "Estudante Teste");
                    estudanteStmt.setString(3, "aluno@isec.pt");
                    estudanteStmt.setString(4, "senha");            // Password (NÃO FAZER ISTO EM PRODUÇÃO!)
                    estudanteStmt.executeUpdate();

                    // Se tudo correu bem, fazer commit da transação
                    connection.commit();
                    System.out.println("[DBManager] Dados iniciais e de teste inseridos com sucesso.");

                } catch (SQLException eInsert) {
                    // Se algo falhou, fazer rollback
                    connection.rollback();
                    System.err.println("[DBManager] Erro ao inserir dados iniciais/teste. Rollback efetuado: " + eInsert.getMessage());
                } finally {
                    // Reativar o auto-commit
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
     * Verifica as credenciais de login na base de dados.
     * @param email O email fornecido pelo utilizador.
     * @param password A password fornecida (em texto simples por agora).
     * @return Um objeto User (Docente ou Estudante) se o login for válido, null caso contrário.
     */
    public User checkLogin(String email, String password) {
        if (connection == null) {
            System.err.println("[DBManager] Tentativa de login sem ligação ativa.");
            return null;
        }

        // Tentar encontrar na tabela Docente
        String sqlDocente = "SELECT id, nome FROM Docente WHERE email = ? AND password = ?";
        try (PreparedStatement pstmtDocente = connection.prepareStatement(sqlDocente)) {
            pstmtDocente.setString(1, email);
            pstmtDocente.setString(2, password); // Comparação direta por agora

            ResultSet rsDocente = pstmtDocente.executeQuery();

            if (rsDocente.next()) {
                // Encontrado como Docente!
                int id = rsDocente.getInt("id");
                String nome = rsDocente.getString("nome");
                System.out.println("[DBManager] Login bem-sucedido (Docente): " + email);
                return new Docente(id, nome, email); // Cria o objeto Docente
            }

        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao verificar login Docente: " + e.getMessage());
            return null; // Retorna null em caso de erro na query
        }

        // Se não encontrou como Docente, tentar na tabela Estudante
        String sqlEstudante = "SELECT id, nome, numero FROM Estudante WHERE email = ? AND password = ?";
        try (PreparedStatement pstmtEstudante = connection.prepareStatement(sqlEstudante)) {
            pstmtEstudante.setString(1, email);
            pstmtEstudante.setString(2, password); // Comparação direta

            ResultSet rsEstudante = pstmtEstudante.executeQuery();

            if (rsEstudante.next()) {
                // Encontrado como Estudante!
                int id = rsEstudante.getInt("id");
                String nome = rsEstudante.getString("nome");
                String numero = rsEstudante.getString("numero");
                System.out.println("[DBManager] Login bem-sucedido (Estudante): " + email);
                // NOTA: Verifica a ordem dos parâmetros no teu construtor Estudante!
                // Assumindo: public Estudante (int id, String name, String email, String studentNumber)
                return new Estudante(id, nome, email, numero); // Cria o objeto Estudante
            }

        } catch (SQLException e) {
            System.err.println("[DBManager] Erro ao verificar login Estudante: " + e.getMessage());
            return null;
        }

        // Se não encontrou em nenhuma tabela
        System.out.println("[DBManager] Login falhou (não encontrado): " + email);
        return null;

    }

    // --- Métodos Futuros ---
    // public int getDbVersion() { ... }
    // public void incrementDbVersion() { ... }
    // etc...

}