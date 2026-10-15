package br.unisa.eventos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nenhum perfil que possa rodar em producao traz segredo de JWT embutido.
 *
 * <p>Os perfis de provedor de IA (groq, openrouter, openai) nasceram para uso local e por isso
 * repetiam o segredo de desenvolvimento do perfil {@code dev}. So que e justamente um deles que
 * se ativa num deploy - {@code SPRING_PROFILES_ACTIVE=groq} na Render, por exemplo. Com o
 * padrao presente, esquecer de definir {@code JWT_SECRET} nao quebraria a subida: a aplicacao
 * entraria no ar assinando token com um segredo que esta publico neste repositorio, e qualquer
 * pessoa poderia forjar um token de organizador.
 *
 * <p>Sem o padrao, a subida falha com a mensagem do {@link SegredoJwtFailureAnalyzer}. Falhar e
 * o comportamento correto: um deploy que nao sobe se conserta em um minuto, um deploy que sobe
 * inseguro nao da sinal nenhum.
 *
 * <p>O perfil {@code dev} fica de fora porque e o unico cujo proposito e nao ir para produção -
 * e o {@code application.yml}, que vale para todos, ja deixa {@code JWT_SECRET} sem padrao.
 */
@DisplayName("Segredo do JWT - nenhum perfil de producao tem padrao")
class SegredoJwtNaoTemPadraoTest {

    private static final List<String> PERFIS_QUE_PODEM_IR_PARA_PRODUCAO =
            List.of("groq", "openrouter", "openai");

    @Test
    @DisplayName("perfil de provedor de IA nao define app.jwt.segredo")
    void nenhumPerfilDeProvedorDefineSegredo() throws IOException {
        for (String perfil : PERFIS_QUE_PODEM_IR_PARA_PRODUCAO) {
            assertThat(propriedade(perfil, "app.jwt.segredo"))
                    .as("o perfil %s nao pode trazer segredo de JWT: ele e ativado em deploy, "
                            + "e um padrao faria a aplicacao subir insegura em silencio", perfil)
                    .isNull();
        }
    }

    @Test
    @DisplayName("o application.yml tambem nao tem padrao, so o placeholder vazio")
    void oArquivoBaseNaoTemPadrao() throws IOException {
        // ${JWT_SECRET:} resolve para string vazia, e e isso que o FailureAnalyzer detecta.
        assertThat(propriedade(null, "app.jwt.segredo")).isEmpty();
    }

    private String propriedade(String perfil, String chave) throws IOException {
        String arquivo = perfil == null
                ? "application.yml"
                : "application-%s.yml".formatted(perfil);

        var ambiente = new StandardEnvironment();
        new YamlPropertySourceLoader()
                .load(String.valueOf(perfil), new ClassPathResource(arquivo))
                .forEach(fonte -> ambiente.getPropertySources().addFirst(fonte));

        return ambiente.getProperty(chave);
    }
}
