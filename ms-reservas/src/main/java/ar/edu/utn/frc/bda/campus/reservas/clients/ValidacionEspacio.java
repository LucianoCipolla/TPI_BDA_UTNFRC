package ar.edu.utn.frc.bda.campus.reservas.clients;

/** Lo que responde ms-espacios al validar un espacio para una franja (su ValidacionDto). */
public record ValidacionEspacio(Long espacioId, String nombre, int capacidad, boolean disponible, String motivo) {
}