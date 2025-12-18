package pt.isec.pd.tp.g11.common.model;

public class Estudante extends User {
    private static final long serialVersionUID = 5L;

    private String studentNumber;

    public Estudante (int id, String name,String email, String studentNumber){
        super(id,name,email);
        this.studentNumber = studentNumber;
    }

    public String getStudentNumber () {return studentNumber;}

    public void setStudentNumber (String studentNumber) {this.studentNumber = studentNumber;}
}
