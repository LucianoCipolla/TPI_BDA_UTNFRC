package ar.edu.utn.frc.bda.campus.espacios.services.validadores;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import ar.edu.utn.frc.bda.campus.espacios.entities.Espacio;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;

/** Laboratorio: de lunes a viernes y con 24 h de anticipación, para preparar los equipos (RN10). */
@Component
public class ValidadorLaboratorio extends ValidadorEspacioBase {

    @Override
    public TipoEspacio tipo() {
        return TipoEspacio.LABORATORIO;
    }

    @Override
    protected String reglasPropias(Espacio e, LocalDateTime inicio, LocalDateTime fin) {
        // RN10: de lunes a viernes
        DayOfWeek dia = inicio.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) {
            return "Los laboratorios solo se reservan de lunes a viernes";
        }
        // RN10: con al menos 24 horas de anticipación
        if (inicio.isBefore(LocalDateTime.now().plusHours(24))) {
            return "Los laboratorios se reservan con al menos 24 horas de anticipación";
        }
        return null;
    }
}