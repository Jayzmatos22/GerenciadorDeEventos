package br.unisa.eventos.config;

import br.unisa.eventos.usuario.SegredoJwtInvalidoException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Troca o stack trace de tres niveis por uma explicacao no topo da saida, com o que fazer.
 *
 * <p>Nao ha segredo padrao em {@code application.yml} de proposito: um valor que "funciona
 * sozinho" acompanharia a aplicacao ate producao e qualquer pessoa poderia forjar um token.
 * Falhar na subida e a decisao certa; o que faltava era a falha ser legivel.
 */
public class SegredoJwtFailureAnalyzer extends AbstractFailureAnalyzer<SegredoJwtInvalidoException> {

    @Override
    protected FailureAnalysis analyze(Throwable falha, SegredoJwtInvalidoException causa) {
        return new FailureAnalysis(
                causa.getMessage(),
                """
                Escolha um destes caminhos:

                  1. Suba com o perfil de desenvolvimento, que ja traz um segredo local:

                         ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
                         (no Windows: mvnw spring-boot:run -Dspring-boot.run.profiles=dev)

                     Na sua IDE, o mesmo efeito vem de definir a variavel de ambiente
                     SPRING_PROFILES_ACTIVE=dev na configuracao de execucao.

                  2. Ou defina a variavel de ambiente JWT_SECRET com no minimo 32 bytes:

                         Linux e macOS:  export JWT_SECRET="$(openssl rand -base64 48)"
                         Windows (cmd):  set JWT_SECRET=um-segredo-longo-de-no-minimo-32-bytes
                         PowerShell:     $env:JWT_SECRET = "um-segredo-longo-de-no-minimo-32-bytes"

                Nao existe segredo padrao fora do perfil dev de proposito: um valor que
                funcionasse sozinho acompanharia a aplicacao ate producao, e ai qualquer
                pessoa poderia forjar um token de acesso.""",
                causa);
    }
}
