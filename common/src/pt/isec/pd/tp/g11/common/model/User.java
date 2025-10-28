package pt.isec.pd.tp.g11.common.model;

import java.io.Serializable;

public abstract class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    private String email;

    public User (int id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public String getNome() { return name; }
    public String getEmail() { return email; }
    public int getId() { return id; }
}
