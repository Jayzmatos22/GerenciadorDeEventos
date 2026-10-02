package br.unisa.eventos.ia;

import br.unisa.eventos.shared.exception.IaIndisponivelException;

import java.util.List;

/** Resumidor de teste, sem chamada de rede. */
public class ResumidorAvaliacoesFake implements ResumidorAvaliacoes {

    private boolean deveFalhar;
    private List<String> ultimosComentarios = List.of();

    public void falharNaProximaChamada() {
        this.deveFalhar = true;
    }

    public void voltarAFuncionar() {
        this.deveFalhar = false;
    }

    public List<String> ultimosComentarios() {
        return ultimosComentarios;
    }

    @Override
    public String resumir(List<String> comentarios) {
        this.ultimosComentarios = List.copyOf(comentarios);

        if (deveFalhar) {
            throw new IaIndisponivelException("Falha simulada do modelo de IA.");
        }
        return "Os participantes elogiaram a organizacao e o conteudo das oficinas. "
                + "Houve criticas ao tamanho do auditorio. "
                + "A sugestao mais recorrente foi ampliar o tempo de cada atividade. "
                + "Resumo gerado a partir de " + comentarios.size() + " comentarios.";
    }
}
