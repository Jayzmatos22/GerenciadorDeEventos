package br.unisa.eventos.config;

import br.unisa.eventos.shared.dto.ErroResponse;
import br.unisa.eventos.usuario.JwtAuthFilter;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Cadeia de seguranca stateless com JWT (secao 7 da especificacao).
 *
 * <p>A autorizacao tem dois niveis: papel aqui e no {@code @PreAuthorize}, e propriedade do
 * recurso dentro de cada service (RN-08). Ownership nao se resolve por anotacao.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final AppProperties propriedades;

    public SecurityConfig(AppProperties propriedades) {
        this.propriedades = propriedades;
    }

    /** RN-11: BCrypt com forca 10. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                    ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        // Infraestrutura e documentacao
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        // Fotos de evento servidas do disco local (secao 13.3)
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // Autenticacao
                        .requestMatchers(HttpMethod.POST, "/api/auth/registrar", "/api/auth/login")
                        .permitAll()
                        // Catalogo publico de eventos: a lista e o detalhe, nada mais.
                        // O padrao casa um unico segmento e so digitos, logo nem
                        // /api/eventos/1/inscricoes nem /api/eventos/meus ficam publicos.
                        .requestMatchers(HttpMethod.GET, "/api/eventos", "/api/eventos/{id:[0-9]+}")
                        .permitAll()
                        // Validacao publica de certificado
                        .requestMatchers(HttpMethod.GET, "/api/certificados/validar/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * No desenvolvimento o frontend usa o proxy do Vite e nem chega a disparar CORS. Isto aqui
     * atende quem sobe o frontend em outra origem, e em producao o valor vem de
     * {@code app.cors.origens}.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOriginPatterns(propriedades.cors().origens());
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));
        configuracao.setExposedHeaders(List.of("Content-Disposition"));

        var fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);
        return fonte;
    }

    /** 401 no mesmo envelope de erro do resto da API. */
    private AuthenticationEntryPoint entryPoint(ObjectMapper objectMapper) {
        return (requisicao, resposta, excecao) -> escrever(objectMapper, requisicao, resposta,
                HttpStatus.UNAUTHORIZED, "CREDENCIAIS_INVALIDAS",
                "Autenticacao necessaria ou token invalido.");
    }

    /** 403 por papel insuficiente; ownership vem do service como AcessoNegadoException. */
    private AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (requisicao, resposta, excecao) -> escrever(objectMapper, requisicao, resposta,
                HttpStatus.FORBIDDEN, "ACESSO_NEGADO",
                "Voce nao tem permissao para esta operacao.");
    }

    private void escrever(ObjectMapper objectMapper, HttpServletRequest requisicao,
                          HttpServletResponse resposta, HttpStatus status, String codigo,
                          String mensagem) throws IOException {
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(resposta.getOutputStream(),
                ErroResponse.de(status.value(), codigo, mensagem, requisicao.getRequestURI()));
    }
}
