package br.unisa.eventos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serve as fotos de evento gravadas em disco local (secao 13.3 da especificacao).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path raizUploads;

    public WebConfig(AppProperties propriedades) {
        this.raizUploads = Path.of(propriedades.storage().diretorio())
                .toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        registro.addResourceHandler("/uploads/**")
                .addResourceLocations(raizUploads.toUri().toString());
    }
}
