package ar.edu.utn.frc.bda.campus.espacios.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Crea el RestClient para Open-Meteo, con URL base y timeouts. */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient openMeteoRestClient(@Value("${campus.clima.url}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));   // máximo para conectar
        factory.setReadTimeout(Duration.ofSeconds(3));      // máximo para esperar la respuesta
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }
}