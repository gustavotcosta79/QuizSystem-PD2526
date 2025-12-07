package pt.isec.pd.tp.g11.client.gui.views;

import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import pt.isec.pd.tp.g11.client.gui.ModelManager;
import pt.isec.pd.tp.g11.common.model.Docente;

public class MainMenuView extends BorderPane {
    private final ModelManager model;
    private final Runnable onLogout;

    public MainMenuView(ModelManager model, Runnable onLogout) {
        this.model = model;
        this.onLogout = onLogout;

        setupUI();
    }

    private void setupUI() {
        // Barra de Ferramentas
        ToolBar toolBar = new ToolBar();
        Label lblUser = new Label("Utilizador: " + model.getCurrentUser().getNome() + " (" + model.getCurrentUser().getEmail() + ")");
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button btnLogout = new Button("Logout");
        btnLogout.setStyle("-fx-text-fill: red;");
        btnLogout.setOnAction(e -> {
            model.logout();
            onLogout.run();
        });

        toolBar.getItems().addAll(lblUser, spacer, btnLogout);
        this.setTop(toolBar);

        //  CENTRO: Painel Específico
        if (model.getCurrentUser() instanceof Docente) {
            this.setCenter(new DocentePane(model));
        } else {
            this.setCenter(new EstudantePane(model));
        }
    }
}