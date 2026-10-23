package ar.edu.utn.frc.bda.campus.espacios.entities.models;

import java.time.LocalTime;
import java.util.Set;

import ar.edu.utn.frc.bda.campus.espacios.entities.Equipamiento;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoActividad;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Lo que manda el ADMIN para crear o modificar un espacio. Con @Valid, un dato mal formado da 400. */
public record EspacioRequest(
        @NotBlank String nombre,
        @NotNull TipoEspacio tipo,
        @Positive int capacidad,
        @DecimalMin("-90.0") @DecimalMax("90.0") double latitud,
        @DecimalMin("-180.0") @DecimalMax("180.0") double longitud,
        @NotNull LocalTime horaApertura,
        @NotNull LocalTime horaCierre,
        @NotEmpty Set<TipoActividad> actividadesPermitidas,
        Set<Equipamiento> equipamiento) {      // puede venir vacío o no venir (por ejemplo, una cancha)
}