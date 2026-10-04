package br.unisa.eventos.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Fixa o fuso horario da JVM.
 *
 * <p>As propriedades de fuso do Jackson e do Hibernate no application.yml cuidam da
 * serializacao e da gravacao, mas nao do {@code LocalDateTime.now()}, que segue o fuso padrao
 * da JVM. E dele que dependem a janela de check-in (RN-04) e a checagem de evento ja terminado
 * (RN-06): num servidor em UTC, as duas deslocariam tres horas em relacao ao horario de
 * Brasilia sem que nada no codigo mudasse.
 */
@Configuration
public class FusoHorarioConfig {

    private static final Logger log = LoggerFactory.getLogger(FusoHorarioConfig.class);

    private final ZoneId fuso;

    public FusoHorarioConfig(AppProperties propriedades) {
        this.fuso = ZoneId.of(propriedades.fusoHorario());
    }

    @PostConstruct
    void fixarFusoPadrao() {
        TimeZone.setDefault(TimeZone.getTimeZone(fuso));
        log.info("Fuso horario da aplicacao fixado em {}", fuso);
    }
}
