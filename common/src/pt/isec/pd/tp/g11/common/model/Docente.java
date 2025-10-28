package pt.isec.pd.tp.g11.common.model;

import java.io.Serializable;

public class Docente extends User {
    private static final long serialVersionUID = 4L;

    public Docente (int id, String name, String email){
        super(id, name, email);
    }
}
