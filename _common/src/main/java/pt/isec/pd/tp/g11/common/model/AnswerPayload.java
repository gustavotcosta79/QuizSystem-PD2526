/*
 * Ficheiro: AnswerPayload.java
 * Objetivo: Representa o "pacote" de dados que o cliente envia
 * ao submeter uma resposta.
 * Responsabilidade: Conter o ID da pergunta e a letra da resposta.
 */
package pt.isec.pd.tp.g11.common.model; // Ou o teu pacote de 'messages'

import java.io.Serializable;

public class AnswerPayload implements Serializable {
    private static final long serialVersionUID = 8L; // Novo ID

    private final int idPergunta;
    private final String respostaLetra; // "a", "b", etc.

    public AnswerPayload(int idPergunta, String respostaLetra) {
        this.idPergunta = idPergunta;
        this.respostaLetra = respostaLetra;
    }

    public int getIdPergunta() { return idPergunta; }
    public String getRespostaLetra() { return respostaLetra; }
}