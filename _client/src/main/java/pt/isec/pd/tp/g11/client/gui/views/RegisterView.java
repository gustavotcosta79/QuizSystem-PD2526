package pt.isec.pd.tp.g11.client.gui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import pt.isec.pd.tp.g11.client.gui.ModelManager;
import pt.isec.pd.tp.g11.common.model.Docente;
import pt.isec.pd.tp.g11.common.model.Estudante;

public class RegisterView extends VBox {
    private final ModelManager model;
    private final Runnable onBackToLogin;

    private TextField txtName, txtEmail, txtSpecial; // Special = Numero estudante ou Codigo Docente
    private PasswordField txtPass, txtConfirm;
    private ComboBox<String> cmbType;
    private Label lblSpecial;

    public RegisterView(ModelManager model, Runnable onBackToLogin) {
        this.model = model;
        this.onBackToLogin = onBackToLogin;

        setupUI();
    }

    private void setupUI() {
        this.setAlignment(Pos.CENTER);
        this.setSpacing(10);
        this.setPadding(new Insets(20));
        this.setStyle("-fx-background-color: #ecf0f1;");

        Label lblTitle = new Label("Registar Nova Conta");
        lblTitle.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        cmbType = new ComboBox<>();
        cmbType.getItems().addAll("Estudante", "Docente");
        cmbType.setValue("Estudante");
        cmbType.setOnAction(e -> updateForm());

        txtName = new TextField(); txtName.setPromptText("Nome Completo");
        txtEmail = new TextField(); txtEmail.setPromptText("Email");

        lblSpecial = new Label("Número de Estudante:");
        txtSpecial = new TextField(); txtSpecial.setPromptText("Nº Estudante");

        txtPass = new PasswordField(); txtPass.setPromptText("Password");
        txtConfirm = new PasswordField(); txtConfirm.setPromptText("Confirmar Password");

        Button btnRegister = new Button("Registar");
        btnRegister.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        btnRegister.setOnAction(e -> handleRegister());

        Button btnBack = new Button("Voltar ao Login");
        btnBack.setOnAction(e -> onBackToLogin.run());

        this.getChildren().addAll(lblTitle, cmbType, txtName, txtEmail, lblSpecial, txtSpecial, txtPass, txtConfirm, btnRegister, btnBack);
        updateForm(); // Configurar labels iniciais
    }

    private void updateForm() {
        if (cmbType.getValue().equals("Docente")) {
            lblSpecial.setText("Código de Registo (Docente):");
            txtSpecial.setPromptText("Código Secreto");
        } else {
            lblSpecial.setText("Número de Estudante:");
            txtSpecial.setPromptText("Nº Estudante");
        }
    }

    private void handleRegister() {
        String nome = txtName.getText();
        String email = txtEmail.getText();
        String special = txtSpecial.getText();
        String p1 = txtPass.getText();
        String p2 = txtConfirm.getText();

        if (nome.isEmpty() || email.isEmpty() || special.isEmpty() || p1.isEmpty()) {
            model.fireNotification("Preencha todos os campos.");
            return;
        }
        if (!p1.equals(p2)) {
            model.fireNotification("As passwords não coincidem.");
            return;
        }

        new Thread(() -> {
            boolean success;
            if (cmbType.getValue().equals("Estudante")) {
                Estudante est = new Estudante(0, nome, email, special);
                success = model.getConnection().registerEstudante(est, p1);
            } else {
                Docente doc = new Docente(0, nome, email);
                int res = model.getConnection().registerDocente(doc, p1, special);
                success = (res == 0);
            }

            Platform.runLater(() -> {
                if (success) {
                    model.fireNotification("Registo efetuado! Faça login.");
                    onBackToLogin.run();
                }
                // Se falhar, o model/connection já deve ter mandado notificação ou tratamos aqui msg genérica
            });
        }).start();
    }
}