package br.unisa.eventos.usuario.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * O BCrypt trabalha sobre no maximo 72 <em>bytes</em>, e o encoder do Spring Security recusa
 * o que passa disso. Limitar por quantidade de caracteres nao resolve: em UTF-8 um acento
 * ocupa dois bytes, e um emoji ate quatro, de modo que uma senha curta em caracteres pode
 * estourar o limite e virar erro 500 no registro.
 *
 * <p>Esta restricao mede em bytes e transforma o caso em erro de validacao do campo, com o
 * 422 e a mensagem que o formulario sabe exibir.
 */
@Documented
@Constraint(validatedBy = SenhaCompativelComBCrypt.Validador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaCompativelComBCrypt {

    String message() default "e longa demais; use no maximo 72 bytes (acentos contam por dois)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<SenhaCompativelComBCrypt, String> {

        private static final int MAXIMO_DE_BYTES = 72;

        @Override
        public boolean isValid(String senha, ConstraintValidatorContext contexto) {
            // Senha ausente ou vazia e problema do @NotBlank, nao desta restricao.
            return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_DE_BYTES;
        }
    }
}
