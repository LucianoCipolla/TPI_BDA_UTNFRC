package ar.edu.utn.frc.bda.campus.espacios.entities.models;

import java.time.LocalTime;
import java.util.Set;

import ar.edu.utn.frc.bda.campus.espacios.entities.Equipamiento;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoActividad;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;

/** Lo que devuelve la API al consultar espacios. */
public record EspacioDto(
    Long id, 
    String nombre, 
    TipoEspacio tipo, 
    int capacidad, 
    LocalTime horaApertura, 
    LocalTime horaCierre, 
    double latitud, 
    double longitud,
    Set<TipoActividad> actividadesPermitidas, 
    Set<Equipamiento> equipamiento) {
}