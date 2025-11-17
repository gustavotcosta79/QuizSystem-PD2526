package pt.isec.pd.tp.g11.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class SubmittedAnswer implements Serializable {

    private static final long serialVersionUID = 11L;

    private final String questionEnunciado;
    private final String myAnswer;
    private final String correctAnswer;
    private final LocalDateTime submissionTime;

    public SubmittedAnswer (String questionEnunciado, String myAnswer, String correctAnswer, LocalDateTime submissionTime){
        this.questionEnunciado = questionEnunciado;
        this.myAnswer = myAnswer;
        this.correctAnswer = correctAnswer;
        this.submissionTime = submissionTime;
    }

    public String getQuestionEnunciado() {
        return questionEnunciado;
    }

    public String getMyAnswer() {
        return myAnswer;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public LocalDateTime getSubmissionTime() {
        return submissionTime;
    }

    public boolean isCorrect (){
        return myAnswer.equalsIgnoreCase(correctAnswer);
    }
}
