package ar.edu.utn.frc.bda.campus.reservas.entities.models;

import java.time.LocalDateTime;

import ar.edu.utn.frc.bda.campus.reservas.entities.EstadoReserva;
import ar.edu.utn.frc.bda.campus.reservas.entities.TipoActividad;

/** Lo que devuelve la API sobre una reserva. */
public record ReservaDto(
        Long id,
        Long espacioId,
        String espacioNombre,
        String usuario,
        LocalDateTime inicio,
        LocalDateTime fin,
        int asistentes,
        TipoActividad actividad,
        EstadoReserva estado) {
}