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

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));

        TextField txtEnun = new TextField(existing != null ? existing.getEnunciado() : "");
        TextField txtStart = new TextField(existing != null ? existing.getBeginDateHour().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) : "");
        TextField txtEnd = new TextField(existing != null ? existing.getEndDateHour().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")) : "");
        txtStart.setPromptText("dd-MM-yyyy HH:mm");
        txtEnd.setPromptText("dd-MM-yyyy HH:mm");

        TextField txtOp1 = new TextField(); TextField txtOp2 = new TextField(); TextField txtOp3 = new TextField();
        TextField txtCorrect = new TextField(existing != null ? existing.getCorrectAnswer() : "");
        txtCorrect.setPromptText("a, b ou c");

        // Se for edição, isto podia ser mais complexo para carregar opções, mas simplificamos
        if (existing != null) model.fireNotification("Aviso: Na edição terá de reescrever as opções.");

        grid.addRow(0, new Label("Enunciado:"), txtEnun);
        grid.addRow(1, new Label("Início:"), txtStart);
        grid.addRow(2, new Label("Fim:"), txtEnd);
        grid.addRow(3, new Label("Opção A:"), txtOp1);
        grid.addRow(4, new Label("Opção B:"), txtOp2);
        grid.addRow(5, new Label("Opção C:"), txtOp3);
        grid.addRow(6, new Label("Correta:"), txtCorrect);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                    LocalDateTime start = LocalDateTime.parse(txtStart.getText(), fmt);
                    LocalDateTime end = LocalDateTime.parse(txtEnd.getText(), fmt);

                    List<Option> ops = new ArrayList<>();
                    ops.add(new Option("a", txtOp1.getText()));
                    ops.add(new Option("b", txtOp2.getText()));
                    ops.add(new Option("c", txtOp3.getText()));

                    return new Question(txtEnun.getText(), start, end, txtCorrect.getText(), ops);
                } catch (Exception e) {
                    model.fireNotification("Dados inválidos: " + e.getMessage());
                    return null;
                }
            }
            return null;
        });

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
                        model.fireNotification("Sucesso!");
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
        alert.setHeaderText("Total Respostas: " + report.results.size());

        StringBuilder sb = new StringBuilder("Lista de Alunos:\n");
        for(QuestionResult r : report.results) {
            sb.append(r.getStudentName()).append(" -> ").append(r.getAnswerGiven()).append("\n");
        }
        TextArea area = new TextArea(sb.toString());
        area.setEditable(false);
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
            // Lógica de escrita CSV (adaptada da ConsoleUI)
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("Nome;Email;Resposta");
                for (QuestionResult r : report.results) {
                    writer.println(r.getStudentName() + ";" + r.getStudentEmail() + ";" + r.getAnswerGiven());
                }
                model.fireNotification("Ficheiro guardado!");
            } catch (Exception e) {
                model.fireNotification("Erro ao gravar: " + e.getMessage());
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