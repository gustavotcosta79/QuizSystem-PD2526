package pt.isec.pd.tp.g11.client.gui.views;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import pt.isec.pd.tp.g11.client.gui.ModelManager;
import pt.isec.pd.tp.g11.client.communication.ServerConnection;
import pt.isec.pd.tp.g11.common.model.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DocentePane extends BorderPane {
    private final ModelManager model;
    private TableView<Question> table;
    private ComboBox<String> cmbFilter;

    public DocentePane(ModelManager model) {
        this.model = model;
        setupUI();
        refreshTable();
    }

    private void setupUI() {
        // --- FILTROS E AÇÕES ---
        HBox topBar = new HBox(10);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER_LEFT);

        cmbFilter = new ComboBox<>();
        cmbFilter.getItems().addAll("Todas", "Ativas", "Futuras", "Expiradas");
        cmbFilter.setValue("Todas");
        cmbFilter.setOnAction(e -> refreshTable());

        Button btnRefresh = new Button("Atualizar");
        btnRefresh.setOnAction(e -> refreshTable());

        Button btnCreate = new Button("Nova Pergunta");
        btnCreate.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        btnCreate.setOnAction(e -> showCreateQuestionDialog());

        topBar.getChildren().addAll(new Label("Filtro:"), cmbFilter, btnRefresh, new Separator(), btnCreate);
        this.setTop(topBar);

        // --- TABELA ---
        table = new TableView<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");

        TableColumn<Question, String> colCode = new TableColumn<>("Código");
        colCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAccessCode()));

        TableColumn<Question, String> colEnun = new TableColumn<>("Enunciado");
        colEnun.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEnunciado()));

        TableColumn<Question, String> colStart = new TableColumn<>("Início");
        colStart.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBeginDateHour().format(fmt)));

        TableColumn<Question, String> colEnd = new TableColumn<>("Fim");
        colEnd.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEndDateHour().format(fmt)));

        table.getColumns().addAll(colCode, colEnun, colStart, colEnd);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        this.setCenter(table);

        // --- BOTÕES DE CONTEXTO (Em baixo) ---
        HBox bottomBar = new HBox(10);
        bottomBar.setPadding(new Insets(10));
        bottomBar.setAlignment(Pos.CENTER);

        Button btnEdit = new Button("Editar Selecionada");
        btnEdit.setOnAction(e -> handleEdit());

        Button btnDelete = new Button("Eliminar");
        btnDelete.setStyle("-fx-text-fill: red;");
        btnDelete.setOnAction(e -> handleDelete());

        Button btnResults = new Button("Ver Resultados / CSV");
        btnResults.setOnAction(e -> handleResults());

        Button btnProfile = new Button("Editar Perfil");
        btnProfile.setOnAction(e -> showEditProfileDialog());

        bottomBar.getChildren().addAll(btnEdit, btnDelete, btnResults, new Separator(), btnProfile);
        this.setBottom(bottomBar);
    }

    private void refreshTable() {
        String filterMap = switch (cmbFilter.getValue()) {
            case "Ativas" -> "ACTIVE";
            case "Futuras" -> "FUTURE";
            case "Expiradas" -> "PAST";
            default -> "ALL";
        };

        new Thread(() -> {
            List<Question> list = model.getConnection().getMyQuestions(filterMap);
            Platform.runLater(() -> {
                if (list != null) {
                    table.setItems(FXCollections.observableArrayList(list));
                }
            });
        }).start();
    }

    private void handleDelete() {
        Question q = table.getSelectionModel().getSelectedItem();
        if (q == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Tem a certeza que quer eliminar " + q.getAccessCode() + "?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.YES) {
                new Thread(() -> {
                    boolean success = model.getConnection().deleteQuestion(q.getAccessCode());
                    Platform.runLater(() -> {
                        if (success) {
                            model.fireNotification("Pergunta eliminada.");
                            refreshTable();
                        } else {
                            model.fireNotification("Erro ao eliminar (já tem respostas?).");
                        }
                    });
                }).start();
            }
        });
    }

    // --- CRIAR / EDITAR PERGUNTA (Simplificado com Dialog) ---
    private void showCreateQuestionDialog() {
        showQuestionDialog(null);
    }

    private void handleEdit() {
        Question q = table.getSelectionModel().getSelectedItem();
        if (q != null) showQuestionDialog(q);
    }

    private void showQuestionDialog(Question existing) {
        Dialog<Question> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Nova Pergunta" : "Editar Pergunta");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true); // Permitir redimensionar a janela

        // 1. Criar um ScrollPane para o caso de ter muitas opções
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(450); // Altura confortável

        VBox root = new VBox(10);
        root.setPadding(new Insets(15));

        // --- CAMPOS FIXOS (Enunciado e Datas) ---
        TextField txtEnun = new TextField(existing != null ? existing.getEnunciado() : "");
        txtEnun.setPromptText("Enunciado da Pergunta");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        TextField txtStart = new TextField(existing != null ? existing.getBeginDateHour().format(fmt) : "");
        txtStart.setPromptText("Início (dd-MM-yyyy HH:mm)");

        TextField txtEnd = new TextField(existing != null ? existing.getEndDateHour().format(fmt) : "");
        txtEnd.setPromptText("Fim (dd-MM-yyyy HH:mm)");

        root.getChildren().addAll(
                new Label("Enunciado:"), txtEnun,
                new Label("Início (dd-MM-yyyy HH:mm):"), txtStart,
                new Label("Fim (dd-MM-yyyy HH:mm):"), txtEnd,
                new Separator()
        );

        // --- OPÇÕES DINÂMICAS ---
        Label lblOp = new Label("Opções (Mínimo 2):");
        VBox optionsBox = new VBox(5); // Caixa onde as opções vão aparecer
        List<TextField> optionFields = new ArrayList<>(); // Lista para guardarmos os TextFields

        // Função auxiliar para adicionar uma nova linha de opção
        Runnable addOptionLine = () -> {
            int index = optionFields.size();
            char letter = (char) ('a' + index); // Gera 'a', 'b', 'c', etc.

            TextField tf = new TextField();
            tf.setPromptText("Texto da opção " + letter);
            HBox.setHgrow(tf, javafx.scene.layout.Priority.ALWAYS);

            Label lblLetter = new Label(letter + ")");
            lblLetter.setMinWidth(25); // Força uma largura mínima de 25px
            lblLetter.setAlignment(Pos.CENTER_RIGHT); // Fica mais bonito alinhado à direita

            HBox row = new HBox(5, lblLetter, tf);
            row.setAlignment(Pos.CENTER_LEFT);

            optionsBox.getChildren().add(row);
            optionFields.add(tf);
        };

        // Estado Inicial: Adiciona 2 opções vazias (mínimo obrigatório)
        // Nota: Na edição, como a lista da tabela não traz as opções, começamos do zero
        if (existing != null) {
            model.fireNotification("Aviso: Na edição, deve reintroduzir as opções.");
        }
        addOptionLine.run(); // Opção A
        addOptionLine.run(); // Opção B

        // Botão para criar mais opções
        Button btnAddOp = new Button("+ Adicionar Opção");
        btnAddOp.setOnAction(e -> addOptionLine.run());

        root.getChildren().addAll(lblOp, optionsBox, btnAddOp, new Separator());

        // --- RESPOSTA CORRETA ---
        TextField txtCorrect = new TextField(existing != null ? existing.getCorrectAnswer() : "");
        txtCorrect.setPromptText("Ex: a");

        root.getChildren().addAll(new Label("Letra da Opção Correta:"), txtCorrect);

        // Finalizar a UI
        scrollPane.setContent(root);
        dialog.getDialogPane().setContent(scrollPane);

        // --- LÓGICA DE CONVERSÃO (Ler os dados) ---
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    // 1. Validar Datas
                    LocalDateTime start = LocalDateTime.parse(txtStart.getText(), fmt);
                    LocalDateTime end = LocalDateTime.parse(txtEnd.getText(), fmt);

                    // 2. Recolher Opções Dinamicamente
                    List<Option> newOptions = new ArrayList<>();
                    for (int i = 0; i < optionFields.size(); i++) {
                        String text = optionFields.get(i).getText().trim();
                        if (text.isEmpty()) throw new Exception("A opção " + (char)('a'+i) + " está vazia.");

                        // Cria a opção com a letra correta (a, b, c...)
                        newOptions.add(new Option(String.valueOf((char)('a' + i)), text));
                    }

                    if (newOptions.size() < 2) throw new Exception("Tem de ter pelo menos 2 opções.");

                    // 3. Validar Resposta Certa
                    String correct = txtCorrect.getText().trim().toLowerCase();
                    if (correct.isEmpty()) throw new Exception("Indique qual é a letra correta.");

                    boolean exists = false;
                    for (Option op : newOptions) {
                        if (op.getLetter().equals(correct)) {
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        // Calcula a última letra disponível para ajudar o utilizador
                        String maxLetter = newOptions.get(newOptions.size() - 1).getLetter();
                        throw new Exception("Opção inválida! Só tem opções de 'a' até '" + maxLetter + "'.");
                    }

                    // Criar o objeto Question
                    return new Question(txtEnun.getText(), start, end, correct, newOptions);

                } catch (Exception e) {
                    model.fireNotification("Erro nos dados: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

        // --- ENVIAR PARA O SERVIDOR ---
        dialog.showAndWait().ifPresent(newQ -> {
            new Thread(() -> {
                boolean success;
                if (existing == null) {
                    success = model.getConnection().createQuestion(newQ) != null;
                } else {
                    success = model.getConnection().editQuestion(existing.getAccessCode(), newQ);
                }
                Platform.runLater(() -> {
                    if (success) {
                        model.fireNotification("Operação realizada com sucesso!");
                        refreshTable();
                    }
                });
            }).start();
        });
    }

    // --- RESULTADOS E CSV ---
    private void handleResults() {
        Question q = table.getSelectionModel().getSelectedItem();
        if (q == null) return;

        new Thread(() -> {
            ServerConnection.QuestionFullReport report = model.getConnection().getQuestionResults(q.getAccessCode());
            Platform.runLater(() -> {
                if (report == null) {
                    model.fireNotification("Não foi possível obter resultados (pergunta ativa ou não é sua).");
                } else {
                    showResultsWindow(report);
                }
            });
        }).start();
    }

    private void showResultsWindow(ServerConnection.QuestionFullReport report) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Resultados: " + report.question.getAccessCode());
        alert.setHeaderText("Relatório de Respostas");

        // Construir o texto com a mesma riqueza de detalhes da Consola
        StringBuilder sb = new StringBuilder();

        // 1. Cabeçalho com detalhes da Pergunta
        sb.append("--- Detalhes da Pergunta ---\n");
        sb.append("Enunciado: ").append(report.question.getEnunciado()).append("\n");
        sb.append("Opção Correta: ").append(report.question.getCorrectAnswer().toUpperCase()).append("\n");
        sb.append("Total Respostas: ").append(report.results.size()).append("\n\n");

        // 2. Lista de Resultados
        sb.append("--- Respostas dos Alunos ---\n");
        if (report.results.isEmpty()) {
            sb.append("(Sem respostas para mostrar)");
        } else {
            for(QuestionResult r : report.results) {
                // Formato: [2012345] Nome do Aluno (email@isec.pt) -> RESPOSTA
                sb.append(String.format("[%s] %s (%s) -> %s\n",
                        r.getStudentNumber(),
                        r.getStudentName(),
                        r.getStudentEmail(),
                        r.getAnswerGiven().toUpperCase()));
            }
        }

        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefSize(500, 300); // Aumentar um pouco o tamanho da janela

        alert.getDialogPane().setContent(area);

        ButtonType btnCsv = new ButtonType("Exportar CSV");
        alert.getButtonTypes().add(btnCsv);

        alert.showAndWait().ifPresent(type -> {
            if (type == btnCsv) exportCSV(report);
        });
    }

    private void exportCSV(ServerConnection.QuestionFullReport report) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Guardar CSV");
        fileChooser.setInitialFileName("resultados_" + report.question.getAccessCode() + ".csv");
        File file = fileChooser.showSaveDialog(getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {

                // Extrair as variáveis para facilitar
                Question q = report.question;
                List<QuestionResult> results = report.results;

                // Formatadores de data/hora (iguais à Consola)
                DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");
                DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

                // --- SECÇÃO 1: Cabeçalho da Pergunta ---
                writer.println("\"dia\";\"hora inicial\";\"hora final\";\"enunciado da pergunta\";\"opção certa\"");

                writer.printf("\"%s\";\"%s\";\"%s\";\"%s\";\"%s\"\n",
                        q.getBeginDateHour().format(dateFmt),
                        q.getBeginDateHour().format(timeFmt),
                        q.getEndDateHour().format(timeFmt),
                        q.getEnunciado(),
                        q.getCorrectAnswer()
                );

                writer.println();

                // --- SECÇÃO 2: Opções ---
                writer.println("\"opção\";\"texto da opção\"");
                for (Option op : q.getOptions()) {
                    writer.printf("\"%s\";\"%s\"\n", op.getLetter(), op.getTextOption());
                }

                writer.println();

                // --- SECÇÃO 3: Respostas dos Alunos ---
                writer.println("\"número de estudante\";\"nome\";\"e-mail\";\"resposta\"");

                for (QuestionResult r : results) {
                    writer.printf("\"%s\";\"%s\";\"%s\";\"%s\"\n",
                            r.getStudentNumber(),
                            r.getStudentName(),
                            r.getStudentEmail(),
                            r.getAnswerGiven()
                    );
                }

                model.fireNotification("Ficheiro CSV guardado com sucesso!");

            } catch (Exception e) {
                model.fireNotification("Erro ao gravar ficheiro: " + e.getMessage());
            }
        }
    }

    private void showEditProfileDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar Perfil Docente");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));
        Docente me = (Docente) model.getCurrentUser();

        TextField txtName = new TextField(me.getNome());
        TextField txtEmail = new TextField(me.getEmail());
        PasswordField txtPass = new PasswordField();

        grid.addRow(0, new Label("Nome:"), txtName);
        grid.addRow(1, new Label("Email:"), txtEmail);
        grid.addRow(2, new Label("Nova Password:"), txtPass);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                Docente update = new Docente(me.getId(), txtName.getText(), txtEmail.getText());
                new Thread(() -> {
                    boolean ok = model.getConnection().updateProfileDocente(update, txtPass.getText());
                    Platform.runLater(() -> {
                        if(ok) model.fireNotification("Perfil atualizado! (Faça logout para ver alterações)");
                        else model.fireNotification("Erro ao atualizar.");
                    });
                }).start();
            }
        });
    }
}