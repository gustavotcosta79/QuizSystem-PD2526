package pt.isec.pd.tp.g11.common.model;

import java.io.Serializable;

public class QuestionResult implements Serializable {

    private static final long SerialVersionUID = 12L;

    private final String studentNumber;
    private final String studentName;
    private final String studentEmail;
    private final String answerGiven;

    public QuestionResult(String studentNumber, String studentName, String studentEmail, String answerGiven) {
        this.studentNumber = studentNumber;
        this.studentName = studentName;
        this.studentEmail = studentEmail;
        this.answerGiven = answerGiven;
    }

    // Getters para mostrar na UI e no CSV
    public String getStudentNumber() { return studentNumber; }
    public String getStudentName() { return studentName; }
    public String getStudentEmail() { return studentEmail; }
    public String getAnswerGiven() { return answerGiven; }

}
