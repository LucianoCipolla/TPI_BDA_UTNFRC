package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

import java.time.Duration;
import java.time.LocalDateTime;

import ar.edu.utn.frc.bda.campus.commons.ReglaNegocioException;

/** RN1 y RN2. Las usan los dos primeros eslabones y también la búsqueda de disponibles. */
public final class ReglasFranja {

    private ReglasFranja() {
    }

    /** RN1: empieza en el futuro y como máximo 14 días después de hoy (el horizonte del pronóstico). */
    public static void validarAnticipacion(LocalDateTime inicio) {
        LocalDateTime ahora = LocalDateTime.now();
        if (!inicio.isAfter(ahora) || inicio.isAfter(ahora.plusDays(14))) {
            throw new ReglaNegocioException("La reserva debe empezar en el futuro y dentro de los próximos 14 días");
        }
    }

    /** RN2: dura entre 30 minutos y 4 horas, dentro del mismo día. */
    public static void validarDuracion(LocalDateTime inicio, LocalDateTime fin) {
        long minutos = Duration.between(inicio, fin).toMinutes();
        if (minutos < 30 || minutos > 240 || !inicio.toLocalDate().equals(fin.toLocalDate())) {
            throw new ReglaNegocioException("La reserva debe durar entre 30 minutos y 4 horas, en el mismo día");
        }
    }

    /** RN1 y RN2 juntas, para la búsqueda de disponibles. */
    public static void validar(LocalDateTime inicio, LocalDateTime fin) {
        validarAnticipacion(inicio);
        validarDuracion(inicio, fin);
    }
}