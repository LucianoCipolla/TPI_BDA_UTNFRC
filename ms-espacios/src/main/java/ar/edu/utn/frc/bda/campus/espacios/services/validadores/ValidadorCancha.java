package ar.edu.utn.frc.bda.campus.espacios.services.validadores;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ar.edu.utn.frc.bda.campus.espacios.clients.OpenMeteoClient;
import ar.edu.utn.frc.bda.campus.espacios.entities.Espacio;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;

/** Cancha (al aire libre): turnos de horas enteras (RN12) y sin lluvia pronosticada (RN4). */
@Component
public class ValidadorCancha extends ValidadorEspacioBase {

    private final OpenMeteoClient openMeteoClient;
    private final int umbralLluvia;

    public ValidadorCancha(OpenMeteoClient openMeteoClient,
        @Value("${campus.clima.umbral-lluvia}") int umbralLluvia) {
        this.openMeteoClient = openMeteoClient;
        this.umbralLluvia = umbralLluvia;
    }

    @Override
    public TipoEspacio tipo() {
        return TipoEspacio.CANCHA;
    }

    @Override
    protected String reglasPropias(Espacio e, LocalDateTime inicio, LocalDateTime fin) {
        // RN12: turnos de horas enteras
        if (Duration.between(inicio, fin).toMinutes() % 60 != 0) {
            return "Las canchas se reservan en turnos de horas enteras";
        }
        // RN4: pronóstico de lluvia (lanza 503 si Open-Meteo falla)
        int lluvia = openMeteoClient.maxProbabilidadLluvia(e.getLatitud(), e.getLongitud(), inicio, fin);
        if (lluvia > umbralLluvia) {
            return "Pronóstico de lluvia de " + lluvia + " %";
        }
        return null;
    }
}