package ar.edu.utn.frc.bda.campus.espacios.clients;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import ar.edu.utn.frc.bda.campus.commons.ServicioNoDisponibleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Consulta a Open-Meteo la probabilidad de lluvia (RN4). Si falla, lanza 503. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenMeteoClient {

    private final RestClient openMeteoRestClient;

    /** Máxima probabilidad de lluvia (%) entre inicio y fin. */
    public int maxProbabilidadLluvia(double lat, double lon, LocalDateTime inicio, LocalDateTime fin) {
        OpenMeteoResponse r;
        try {
            r = openMeteoRestClient.get()
                    .uri(u -> u.path("/v1/forecast")
                            .queryParam("latitude", lat)
                            .queryParam("longitude", lon)
                            .queryParam("hourly", "precipitation_probability")
                            .queryParam("timezone", "America/Argentina/Cordoba")
                            .queryParam("start_date", inicio.toLocalDate())
                            .queryParam("end_date", fin.toLocalDate())
                            .build())
                    .retrieve()
                    .body(OpenMeteoResponse.class);
        } catch (RestClientException ex) {               // no conecta, timeout o error HTTP
            log.warn("No se pudo consultar el clima en Open-Meteo (lat {}, lon {}) para el {} → 503 | causa: {}",
                    lat, lon, inicio.toLocalDate(), ex.getMessage());
            throw new ServicioNoDisponibleException("No se pudo consultar el pronóstico del clima");
        }

        if (r == null || r.hourly() == null || r.hourly().time() == null || r.hourly().lluvia() == null
                || r.hourly().time().size() != r.hourly().lluvia().size()) {
            log.warn("Open-Meteo respondió, pero sin los datos de lluvia esperados (lat {}, lon {}) para el {} → 503",
                    lat, lon, inicio.toLocalDate());
            throw new ServicioNoDisponibleException("Respuesta inesperada del servicio de clima");
        }

        int max = -1;
        LocalDateTime desde = inicio.truncatedTo(ChronoUnit.HOURS);
        for (int i = 0; i < r.hourly().time().size(); i++) {
            LocalDateTime hora = LocalDateTime.parse(r.hourly().time().get(i));
            Integer p = r.hourly().lluvia().get(i);
            if (!hora.isBefore(desde) && hora.isBefore(fin) && p != null) {
                max = Math.max(max, p);
            }
        }
        if (max < 0) {
            throw new ServicioNoDisponibleException("El pronóstico no cubre la franja pedida");
        }
        return max;
    }
}