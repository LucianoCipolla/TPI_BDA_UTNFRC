package ar.edu.utn.frc.bda.campus.reservas.entities.models;

import java.time.LocalDateTime;
import java.util.Set;

import ar.edu.utn.frc.bda.campus.reservas.entities.TipoActividad;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Lo que manda el usuario para reservar. El usuario no viene acá: sale del token (header X-Usuario). */
public record CrearReservaRequest(
        @NotNull Long espacioId,
        @NotNull LocalDateTime inicio,
        @NotNull LocalDateTime fin,
        @Positive int asistentes,
        @NotNull TipoActividad actividad,
        Set<String> equipamiento) {      // por ejemplo ["PROYECTOR"]; puede venir vacío
}