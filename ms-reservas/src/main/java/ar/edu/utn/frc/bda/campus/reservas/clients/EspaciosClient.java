package ar.edu.utn.frc.bda.campus.reservas.clients;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import ar.edu.utn.frc.bda.campus.commons.ApiResponse;
import ar.edu.utn.frc.bda.campus.commons.RecursoNoEncontradoException;
import ar.edu.utn.frc.bda.campus.commons.ReglaNegocioException;
import ar.edu.utn.frc.bda.campus.commons.ServicioNoDisponibleException;
import ar.edu.utn.frc.bda.campus.reservas.entities.TipoActividad;
import lombok.RequiredArgsConstructor;

/** Cliente HTTP hacia ms-espacios. Traduce sus errores (y su ausencia) a errores propios. */
@Component
@RequiredArgsConstructor
public class EspaciosClient {

    private static final String NO_DISPONIBLE = "El servicio de espacios no está disponible";

    private final RestClient espaciosRestClient;

    /** Para crear una reserva: ¿este espacio sirve en esta franja? */
    public ValidacionEspacio validar(Long espacioId, LocalDateTime inicio, LocalDateTime fin, TipoActividad actividad, Set<String> equipamiento) {
        try {
            ApiResponse<ValidacionEspacio> r = espaciosRestClient.get()
                    .uri(u -> u.path("/api/espacios/{id}/validacion")
                            .queryParam("inicio", inicio)
                            .queryParam("fin", fin)
                            .queryParam("actividad", actividad)
                            .queryParam("equipamiento", vacioSiNulo(equipamiento).toArray())
                            .build(espacioId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<ValidacionEspacio>>() { });
            return contenido(r);
        } catch (HttpClientErrorException.NotFound ex) {                 // el espacio no existe → 404
            throw new RecursoNoEncontradoException(mensajeRemoto(ex, "No existe el espacio " + espacioId));
        } catch (HttpClientErrorException ex) {                          // pedido inválido para ms-espacios → 422
            throw new ReglaNegocioException(mensajeRemoto(ex, "El pedido no es válido para el espacio"));
        } catch (HttpServerErrorException.ServiceUnavailable ex) {       // ms-espacios sin pronóstico → 503
            throw new ServicioNoDisponibleException(mensajeRemoto(ex, "No se pudo verificar el clima del espacio"));
        } catch (RestClientException ex) {                               // caído, timeout u otro error → 503
            throw new ServicioNoDisponibleException(NO_DISPONIBLE);
        }
    }

    /** Para la búsqueda: espacios que cumplen capacidad, actividad, equipamiento, horario y clima. */
    public List<EspacioDisponible> compatibles(LocalDateTime inicio, LocalDateTime fin, int capacidad, TipoActividad actividad, Set<String> equipamiento) {
        try {
            ApiResponse<List<EspacioDisponible>> r = espaciosRestClient.get()
                    .uri(u -> u.path("/api/espacios/compatibles")
                            .queryParam("inicio", inicio)
                            .queryParam("fin", fin)
                            .queryParam("capacidad", capacidad)
                            .queryParam("actividad", actividad)
                            .queryParam("equipamiento", vacioSiNulo(equipamiento).toArray())
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<List<EspacioDisponible>>>() { });
            return contenido(r);
        } catch (HttpClientErrorException ex) {                          // pedido inválido → 422
            throw new ReglaNegocioException(mensajeRemoto(ex, "El pedido de búsqueda no es válido"));
        } catch (RestClientException ex) {                               // caído, timeout u otro error → 503
            throw new ServicioNoDisponibleException(NO_DISPONIBLE);
        }
    }

    /** Un 200 sin datos también es una respuesta inesperada. */
    private static <T> T contenido(ApiResponse<T> r) {
        if (r == null || r.responseValue() == null) {
            throw new ServicioNoDisponibleException("Respuesta inesperada del servicio de espacios");
        }
        return r.responseValue();
    }

    /** El mensaje del ApiResponse de error que mandó ms-espacios, o uno por defecto si no se puede leer. */
    private static String mensajeRemoto(RestClientResponseException ex, String porDefecto) {
        try {
            ApiResponse<Void> r = ex.getResponseBodyAs(new ParameterizedTypeReference<ApiResponse<Void>>() { });
            if (r != null && r.message() != null) {
                return r.message();
            }
        } catch (RuntimeException ignorada) {
            // el cuerpo no es un ApiResponse: se usa el mensaje por defecto
        }
        return porDefecto;
    }

    private static Set<String> vacioSiNulo(Set<String> s) {
        return s == null ? Set.of() : s;
    }
}