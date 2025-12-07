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

    public String getLetter() {
        return letter;
    }

    public String getTextOption() {
        return textOption;
    }

}
