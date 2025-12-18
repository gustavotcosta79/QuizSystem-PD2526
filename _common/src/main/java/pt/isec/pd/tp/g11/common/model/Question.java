package pt.isec.pd.tp.g11.common.model;


import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class Question implements Serializable {

    private static final long serialVersionUID = 7L;

    private int id;
    private int idDocente;
    private String accessCode;
    private String enunciado;
    private LocalDateTime beginDateHour;
    private LocalDateTime endDateHour;
    private String correctAnswer; // ex: "a"
    private List<Option> options; // A pergunta contém as suas opçõe

    public Question (String enunciado, LocalDateTime beginDateHour, LocalDateTime endDateHour, String correctAnswer, List<Option> options) {
        this.enunciado = enunciado;
        this.beginDateHour = beginDateHour;
        this.endDateHour = endDateHour;
        this.correctAnswer = correctAnswer;
        this.options = options;
    }

    // Getters e Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdDocente() {
        return idDocente;
    }

    public void setIdDocente(int newIdDocente) {
        this.idDocente = newIdDocente;
    }

    public String getAccessCode() {
        return accessCode;
    }

    public void setAccessCode(String newAccessCode) {
        this.accessCode = newAccessCode;
    }

    public String getEnunciado() {
        return enunciado;
    }

    public LocalDateTime getBeginDateHour() {
        return beginDateHour;
    }

    public LocalDateTime getEndDateHour() {
        return endDateHour;
    }

    public String getCorrectAnswer() {
        return correctAnswer;
    }

    public List<Option> getOptions() {
        return options;
    }

}
