package br.unisa.eventos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Infraestrutura das chamadas de IA.
 *
 * <p>Nao ha bean de ChatClient aqui de proposito: quem precisa dele pega o
 * {@code ChatClient.Builder} por ObjectProvider, porque o bean simplesmente nao existe quando
 * nenhum modelo esta configurado, e isso tem que virar IA_INDISPONIVEL em tempo de chamada e
 * nao uma falha na subida da aplicacao (RN-10).
 */
@Configuration
public class AiConfig {

    /**
     * As chamadas ao modelo rodam aqui para que o timeout de app.ia.timeout-segundos possa ser
     * aplicado de fora, sem depender do cliente HTTP de cada provedor. Threads virtuais porque
     * a tarefa e espera de rede, nao processamento.
     */
    @Bean(destroyMethod = "close")
    ExecutorService executorIa() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
