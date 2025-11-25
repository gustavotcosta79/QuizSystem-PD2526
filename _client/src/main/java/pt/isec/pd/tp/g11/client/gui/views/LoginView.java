package pt.isec.pd.tp.g11.client.gui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import pt.isec.pd.tp.g11.client.gui.ModelManager;

public class LoginView extends VBox {
    private final ModelManager model;
    private final Runnable onLoginSuccess;
    private final Runnable onGoToRegister; // Novo callback

    public LoginView(ModelManager model, Runnable onLoginSuccess, Runnable onGoToRegister) {
        this.model = model;
        this.onLoginSuccess = onLoginSuccess;
        this.onGoToRegister = onGoToRegister;

        setupUI();
    }

    private void setupUI() {
        this.setAlignment(Pos.CENTER);
        this.setSpacing(15);
        this.setPadding(new Insets(20));
        this.setStyle("-fx-background-color: #f0f4f8;");

        Label lblTitle = new Label("Quiz System - Login");
        lblTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        TextField txtEmail = new TextField("docente@isec.pt"); // default para teste
        txtEmail.setPromptText("Email");
        txtEmail.setMaxWidth(300);

        PasswordField txtPass = new PasswordField();
        txtPass.setPromptText("Password");
        txtPass.setMaxWidth(300);

        Button btnLogin = new Button("Entrar");
        btnLogin.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14px;");
        btnLogin.setPrefWidth(150);

        Hyperlink linkRegister = new Hyperlink("Não tem conta? Registe-se aqui.");
        linkRegister.setOnAction(e -> onGoToRegister.run());

        Label lblStatus = new Label();
        lblStatus.setStyle("-fx-text-fill: red;");

        btnLogin.setOnAction(e -> {
            String email = txtEmail.getText();
            String pass = txtPass.getText();

            if (email.isEmpty() || pass.isEmpty()) {
                lblStatus.setText("Preencha todos os campos.");
                return;
            }

            lblStatus.setText("A ligar...");
            btnLogin.setDisable(true);

            new Thread(() -> {
                boolean success = model.login(email, pass);
                javafx.application.Platform.runLater(() -> {
                    btnLogin.setDisable(false);
                    if (success) {
                        onLoginSuccess.run();
                    } else {
                        lblStatus.setText("Login falhou. Verifique servidor ou credenciais.");
                    }
                });
            }).start();
        });

        this.getChildren().addAll(lblTitle, txtEmail, txtPass, btnLogin, linkRegister, lblStatus);
    }
}