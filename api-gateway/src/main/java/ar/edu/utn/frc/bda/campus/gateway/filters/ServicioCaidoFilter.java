package ar.edu.utn.frc.bda.campus.gateway.filters;

import java.net.ConnectException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import ar.edu.utn.frc.bda.campus.commons.ApiResponse;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * Si el microservicio de destino no responde (contenedor apagado o caído), el
 * gateway
 * responde 503 con nuestro ApiResponse, en vez del 500 genérico de Spring Cloud
 * Gateway.
 */
@Component
public class ServicioCaidoFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ServicioCaidoFilter.class);

    private final JsonMapper jsonMapper;

    public ServicioCaidoFilter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Primero en la cadena: así envuelve a todos los demás, incluido el que reenvía
     * el pedido.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(ServicioCaidoFilter::esServicioCaido, ex -> {
                    Route ruta = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    log.warn("Falla 503 Service Unavailable | {} {} | el microservicio de destino ({}) no responde"
                            + " (¿contenedor apagado?) | causa: {}",
                            exchange.getRequest().getMethod(), exchange.getRequest().getPath(),
                            ruta != null ? ruta.getUri() : "desconocido", ex.getMessage());
                    return responder503(exchange.getResponse());
                });
    }

    /**
     * No se pudo conectar (servicio apagado) o no existe el host (contenedor
     * detenido).
     */
    private static boolean esServicioCaido(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> responder503(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] json = jsonMapper.writeValueAsBytes(
                ApiResponse.create(false, null, "El servicio no está disponible. Intentá de nuevo en unos minutos."));
        DataBuffer buffer = response.bufferFactory().wrap(json);
        return response.writeWith(Mono.just(buffer));
    }
}
