package ar.edu.utn.frc.bda.campus.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Las rutas: cada path va al microservicio que es dueño de ese recurso. */
@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator rutas(RouteLocatorBuilder builder,
            @Value("${campus.ms-espacios-url}") String espacios,
            @Value("${campus.ms-reservas-url}") String reservas) {
        return builder.routes()
                .route("espacios", r -> r.path("/api/espacios/**").uri(espacios))
                .route("reservas", r -> r.path("/api/reservas/**").uri(reservas))
                .build();
    }
}