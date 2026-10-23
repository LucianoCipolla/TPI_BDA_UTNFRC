package ar.edu.utn.frc.bda.campus.espacios.services.validadores;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import ar.edu.utn.frc.bda.campus.espacios.entities.Espacio;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;

/** Sala: máximo 2 horas (RN11). */
@Component
public class ValidadorSala extends ValidadorEspacioBase {

    @Override
    public TipoEspacio tipo() {
        return TipoEspacio.SALA;
    }

    @Override
    protected String reglasPropias(Espacio e, LocalDateTime inicio, LocalDateTime fin) {
        // RN11: máximo 2 horas
        if (Duration.between(inicio, fin).toMinutes() > 120) {
            return "Las salas se reservan por un máximo de 2 horas";
        }
        return null;
    }
}