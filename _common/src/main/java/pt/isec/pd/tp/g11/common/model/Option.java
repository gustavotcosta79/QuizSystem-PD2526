package pt.isec.pd.tp.g11.common.model;

import java.io.Serializable;

public class Option implements Serializable {
    private static final long serialVersionUID = 6L;
    private int id;
    private int questionId;
    private String letter;
    private String textOption; ///???? ex que dá é: "Socket"

    public Option(String letter, String textOption) {
        this.letter =  letter;
        this.textOption = textOption;
    }

    // Getters e Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    public String getLetter() {
        return letter;
    }

    public void setLetter(String letter) {
        this.letter = letter;
    }

    public String getTextOption() {
        return textOption;
    }

    public void setTextOption (String textOption) {
        this.textOption = textOption;
    }
}
