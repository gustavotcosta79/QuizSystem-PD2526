package pt.isec.pd.tp.g11.client.gui;

import javafx.application.Platform;
import pt.isec.pd.tp.g11.client.communication.ServerConnection;
import pt.isec.pd.tp.g11.common.model.*;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class ModelManager {
    private final ServerConnection connection;
    private final PropertyChangeSupport pcs;
    private User currentUser;

    public ModelManager() throws Exception {
        // Inicializa com a diretoria hardcoded ou passada por argumento no Main
        // Aqui assumimos localhost para facilitar, mas o ideal é vir do Main
        this.connection = new ServerConnection("127.0.0.1:5000");
        this.pcs = new PropertyChangeSupport(this);
    }

    // Construtor alternativo se quisermos passar IP
    public ModelManager(String dirIp, int dirPort) throws Exception {
        this.connection = new ServerConnection(dirIp + ":" + dirPort);
        this.pcs = new PropertyChangeSupport(this);
    }

    public void startNetwork() {
        if (!connection.findServer()) {
            fireNotification("ERRO FATAL: Não foi possível encontrar o servidor.");
            return; // GUI deve tratar disto
        }

        // Configurar o callback de notificações
        connection.setNotificationCallback((msg) -> {
            Platform.runLater(() -> {
                fireNotification(msg);
            });
        });
    }

    public boolean login(String email, String password) {
        User user = connection.login(email, password);
        if (user != null) {
            this.currentUser = user;
            return true;
        }
        return false;
    }

    public void logout() {
        connection.closeConnection();
        this.currentUser = null;
    }

    public User getCurrentUser() { return currentUser; }
    public ServerConnection getConnection() { return connection; }

    // --- Sistema de Eventos ---
    public void addPropertyChangeListener(String property, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(property, listener);
    }

    public void fireNotification(String msg) {
        pcs.firePropertyChange("NOTIFICATION", null, msg);
    }
}