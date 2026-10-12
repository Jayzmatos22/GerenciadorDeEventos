package br.unisa.eventos.ia;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ordem em que os modelos configurados sao tentados numa chamada.
 *
 * <p>// DECISAO: a ordem e circular, nao fixa.
 *
 * <p>Os endpoints gratuitos do OpenRouter sao capacidade compartilhada entre todos os usuarios
 * do gateway, entao recusam com 429 ("Provider returned error") mesmo na primeira requisicao da
 * conta. Uma lista percorrida sempre do inicio concentraria todas as chamadas no primeiro
 * modelo e so usaria os outros quando ele recusasse; comecando de um ponto diferente a cada
 * chamada, a carga se espalha e a recusa de um nao vira espera para todos.
 *
 * <p>Depois do ponto de partida a ordem e sequencial e cobre a lista inteira: a rotacao decide
 * por onde comecar, nao quantos tentar.
 *
 * <p>A instancia e compartilhada entre threads (um bean por aplicacao), e o contador por isso e
 * atomico. O valor so escolhe por onde comecar, entao uma corrida perdida custa no maximo duas
 * chamadas saindo do mesmo ponto - nada que exija exclusao mutua.
 */
class RotacaoDeModelos {

    private final List<String> modelos;
    private final AtomicInteger proximoInicio = new AtomicInteger();

    RotacaoDeModelos(List<String> configurados) {
        // O valor chega de variavel de ambiente separada por virgula, entao espaco em volta e
        // entrada vazia (de uma virgula sobrando) sao esperados. Repetido tambem: tentar o
        // mesmo modelo duas vezes na mesma chamada so gastaria o orcamento de tempo.
        var semRepetir = new LinkedHashSet<String>();
        if (configurados != null) {
            configurados.stream()
                    .filter(modelo -> modelo != null && !modelo.isBlank())
                    .map(String::trim)
                    .forEach(semRepetir::add);
        }
        this.modelos = List.copyOf(semRepetir);
    }

    /** Vazia quando nenhum modelo foi configurado: vale o que o Spring AI ja tem. */
    List<String> ordemDaVez() {
        if (modelos.isEmpty()) {
            return List.of();
        }
        // floorMod e nao %: o getAndIncrement estoura para negativo depois de 2^31 chamadas, e
        // o resto de um negativo em Java e negativo.
        int inicio = Math.floorMod(proximoInicio.getAndIncrement(), modelos.size());

        List<String> ordem = new ArrayList<>(modelos.size());
        ordem.addAll(modelos.subList(inicio, modelos.size()));
        ordem.addAll(modelos.subList(0, inicio));
        return List.copyOf(ordem);
    }

    int quantidade() {
        return modelos.size();
    }
}
