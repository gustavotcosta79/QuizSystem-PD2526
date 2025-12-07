package pt.isec.pd.tp.g11.client.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import pt.isec.pd.tp.g11.client.gui.views.LoginView;
import pt.isec.pd.tp.g11.client.gui.views.MainMenuView;
import pt.isec.pd.tp.g11.client.gui.views.RegisterView;

public class MainJFX extends Application {

    private ModelManager model;

    @Override
    public void init() throws Exception {
        // Assume localhost por defeito.
        String ip = "127.0.0.1";
        if (!getParameters().getRaw().isEmpty()) {
            ip = getParameters().getRaw().get(0).split(":")[0];
        }
        model = new ModelManager(ip, 5000);
        model.startNetwork();
    }

    @Override
    public void start(Stage stage) {
        model.addPropertyChangeListener("NOTIFICATION", evt -> {
            String msg = (String) evt.getNewValue();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Notificação");
                alert.setHeaderText(null);
                alert.setContentText(msg);
                alert.show();
            });
        });

        stage.setTitle("Sistema de Perguntas P.D. (GUI)");
        showLoginScreen(stage);
        stage.show();
    }

    private void showLoginScreen(Stage stage) {
        LoginView loginRoot = new LoginView(
                model,
                () -> showMainScreen(stage),    // Sucesso no Login
                () -> showRegisterScreen(stage) // Ir para Registo
        );
        Scene scene = new Scene(loginRoot, 500, 400);
        stage.setScene(scene);
    }

    private void showRegisterScreen(Stage stage) {
        RegisterView regRoot = new RegisterView(
                model,
                () -> showLoginScreen(stage) // Voltar ao Login
        );
        Scene scene = new Scene(regRoot, 500, 500);
        stage.setScene(scene);
    }

    private void showMainScreen(Stage stage) {
        MainMenuView mainRoot = new MainMenuView(model, () -> showLoginScreen(stage));
        Scene scene = new Scene(mainRoot, 900, 600);
        stage.setScene(scene);
    }

    @Override
    public void stop() throws Exception {
        model.logout();
        super.stop();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}