package pt.isec.pd.tp.g11.common.model;

import java.io.Serializable;

public class Estudante extends User {
    private static final long serialVersionUID = 5L;

    private String studentNumber;

    public Estudante (int id, String email, String name, String studentNumber){
        super(id,email,name);
        this.studentNumber = studentNumber;
    }

    public String getStudentNumber () {return studentNumber;}

    public void setStudentNumber (String studentNumber) {this.studentNumber = studentNumber;}
}
