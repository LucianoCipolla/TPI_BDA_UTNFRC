package ar.edu.utn.frc.bda.campus.gateway.filters;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Pasa a los microservicios quién es el usuario, sacándolo del JWT ya validado
 * (D8).
 */
@Component
public class UsuarioHeaderFilter implements GlobalFilter {

    private static final String HEADER = "X-Usuario";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Primero se borra cualquier X-Usuario que mande el cliente: no se le puede
        // creer
        ServerWebExchange limpio = exchange.mutate()
                .request(r -> r.headers(h -> h.remove(HEADER)))
                .build();
        return limpio.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .map(auth -> limpio.mutate()
                        .request(r -> r.headers(h -> h.set(HEADER,
                                auth.getToken().getClaimAsString("preferred_username"))))
                        .build())
                .defaultIfEmpty(limpio)
                .flatMap(chain::filter);
    }
}