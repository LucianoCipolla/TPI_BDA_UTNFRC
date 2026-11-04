package ar.edu.utn.frc.bda.campus.gateway.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security Filter del gateway: autentica (JWT válido; si no, 401) y autoriza
 * (rol por ruta; si no, 403).
 * Spring aplica la primera regla que coincide: por eso cada GET va antes que la
 * regla general de su ruta.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilter(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // API sin cookies ni sesión: el token viaja en un header
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.GET, "/api/espacios/**").hasAnyRole("USUARIO", "ADMIN")
                        .pathMatchers("/api/espacios/**").hasRole("ADMIN") // alta, modificación, baja
                        .pathMatchers(HttpMethod.GET, "/api/reservas").hasRole("ADMIN") // todas las reservas (con
                                                                                        // ?estado=...)
                        .pathMatchers(HttpMethod.PATCH, "/api/reservas/*/aprobar", "/api/reservas/*/rechazar")
                        .hasRole("ADMIN")
                        .pathMatchers("/api/reservas/**").hasRole("USUARIO") // crear, mias, cancelar, disponibilidad
                        .anyExchange().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(rolesDeKeycloak())))
                .build();
    }

    /**
     * Keycloak manda los roles en realm_access.roles; Spring los espera como
     * ROLE_X.
     */
    private ReactiveJwtAuthenticationConverterAdapter rolesDeKeycloak() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realm = jwt.getClaimAsMap("realm_access");
            if (realm == null || !(realm.get("roles") instanceof Collection<?> roles)) {
                return List.of();
            }
            return roles.stream()
                    .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                    .toList();
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}