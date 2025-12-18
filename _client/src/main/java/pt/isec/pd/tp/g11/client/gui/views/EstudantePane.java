package pt.isec.pd.tp.g11.client.gui.views;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import pt.isec.pd.tp.g11.client.gui.ModelManager;
import pt.isec.pd.tp.g11.common.model.*;

import java.util.List;

public class EstudantePane extends BorderPane {
    private final ModelManager model;

    // UI Elements
    private TextField txtCode;
    private VBox questionBox; // Onde a pergunta aparece
    private ToggleGroup groupOptions;
    private Button btnSubmit;
    private TableView<SubmittedAnswer> historyTable;

    public EstudantePane(ModelManager model) {
        this.model = model;
        setupUI();
    }

    private void setupUI() {
        TabPane tabs = new TabPane();

        Tab tabAnswer = new Tab("Responder", createAnswerPane());
        tabAnswer.setClosable(false);

        Tab tabHistory = new Tab("Histórico", createHistoryPane());
        tabHistory.setClosable(false);
        tabHistory.setOnSelectionChanged(e -> {
            if (tabHistory.isSelected()) loadHistory();
        });

        Tab tabProfile = new Tab("Perfil", createProfilePane());
        tabProfile.setClosable(false);

        tabs.getTabs().addAll(tabAnswer, tabHistory, tabProfile);
        this.setCenter(tabs);
    }

    private VBox createAnswerPane() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.TOP_CENTER);

        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER);
        txtCode = new TextField();
        txtCode.setPromptText("Código da Pergunta");
        Button btnSearch = new Button("Procurar");
        btnSearch.setOnAction(e -> searchQuestion());
        searchBox.getChildren().addAll(new Label("Código:"), txtCode, btnSearch);

        // Área da Pergunta (escondida inicialmente)
        questionBox = new VBox(10);
        questionBox.setStyle("-fx-border-color: #bdc3c7; -fx-padding: 15; -fx-background-color: white;");
        questionBox.setVisible(false);

        groupOptions = new ToggleGroup();
        btnSubmit = new Button("Submeter Resposta");
        btnSubmit.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white;");

        box.getChildren().addAll(searchBox, questionBox);
        return box;
    }

    private void searchQuestion() {
        String code = txtCode.getText().trim();
        if (code.isEmpty()) return;

        new Thread(() -> {
            Question q = model.getConnection().getQuestionByCode(code);
            Platform.runLater(() -> {
                if (q == null) {
                    model.fireNotification("Pergunta não encontrada ou inativa.");
                    questionBox.setVisible(false);
                } else {
                    displayQuestion(q);
                }
            });
        }).start();
    }

    private void displayQuestion(Question q) {
        questionBox.getChildren().clear();
        questionBox.setVisible(true);

        Label lblEnun = new Label(q.getEnunciado());
        lblEnun.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        questionBox.getChildren().add(lblEnun);

        groupOptions.getToggles().clear();
        for (Option op : q.getOptions()) {
            RadioButton rb = new RadioButton(op.getLetter() + ") " + op.getTextOption());
            rb.setUserData(op.getLetter());
            rb.setToggleGroup(groupOptions);
            questionBox.getChildren().add(rb);
        }

        btnSubmit.setOnAction(e -> submitAnswer(q.getId()));
        questionBox.getChildren().add(new Separator());
        questionBox.getChildren().add(btnSubmit);
    }

    private void submitAnswer(int questionId) {
        Toggle selected = groupOptions.getSelectedToggle();
        if (selected == null) {
            model.fireNotification("Selecione uma opção!");
            return;
        }
        String answer = (String) selected.getUserData();

        new Thread(() -> {
            boolean ok = model.getConnection().submitAnswer(questionId, answer);
            Platform.runLater(() -> {
                if (ok) {
                    model.fireNotification("Resposta submetida com sucesso!");
                    questionBox.setVisible(false);
                    txtCode.clear();
                } else {
                    model.fireNotification("Erro ao submeter (já respondeu?).");
                }
            });
        }).start();
    }

    private VBox createHistoryPane() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(20));

        historyTable = new TableView<>();

        TableColumn<SubmittedAnswer, String> colQ = new TableColumn<>("Pergunta");
        colQ.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getQuestionEnunciado()));

        TableColumn<SubmittedAnswer, String> colMy = new TableColumn<>("Minha Resp.");
        colMy.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMyAnswer()));

        TableColumn<SubmittedAnswer, String> colRight = new TableColumn<>("Correta");
        colRight.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCorrectAnswer()));

        TableColumn<SubmittedAnswer, String> colRes = new TableColumn<>("Resultado");
        colRes.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isCorrect() ? "CERTA" : "ERRADA"));

        historyTable.getColumns().addAll(colQ, colMy, colRight, colRes);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button btnRefresh = new Button("Atualizar Histórico");
        btnRefresh.setOnAction(e -> loadHistory());

        box.getChildren().addAll(btnRefresh, historyTable);
        return box;
    }

    private void loadHistory() {
        new Thread(() -> {
            List<SubmittedAnswer> list = model.getConnection().getMyAnswers();
            Platform.runLater(() -> {
                if (list != null) historyTable.setItems(FXCollections.observableArrayList(list));
            });
        }).start();
    }

    private VBox createProfilePane() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.CENTER);

        Estudante me = (Estudante) model.getCurrentUser();
        TextField txtName = new TextField(me.getNome());
        TextField txtEmail = new TextField(me.getEmail());
        TextField txtNum = new TextField(me.getStudentNumber());
        PasswordField txtPass = new PasswordField(); txtPass.setPromptText("Nova password (opcional)");

        Button btnSave = new Button("Atualizar Perfil");
        btnSave.setOnAction(e -> {
            Estudante update = new Estudante(me.getId(), txtName.getText(), txtEmail.getText(), txtNum.getText());
            new Thread(() -> {
                boolean ok = model.getConnection().updateProfileEstudante(update, txtPass.getText());
                Platform.runLater(() -> {
                    if(ok) model.fireNotification("Perfil atualizado! Logout para ver alterações.");
                    else model.fireNotification("Erro ao atualizar (dados duplicados?).");
                });
            }).start();
        });

        box.getChildren().addAll(new Label("Editar Perfil"), txtName, txtEmail, txtNum, txtPass, btnSave);
        return box;
    }
}