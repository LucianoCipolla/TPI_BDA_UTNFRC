package ar.edu.utn.frc.bda.campus.espacios.entities.models;

/** Lo que ms-reservas necesita saber del espacio para una franja. */
public record ValidacionDto(
    Long espacioId, 
    String nombre, 
    int capacidad, 
    boolean disponible, 
    String motivo) {
}