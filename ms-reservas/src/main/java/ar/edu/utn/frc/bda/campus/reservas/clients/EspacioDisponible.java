package ar.edu.utn.frc.bda.campus.reservas.clients;

import java.util.Set;

/** Un espacio de la búsqueda de compatibles de ms-espacios, con los campos que usamos. */
public record EspacioDisponible(Long id, String nombre, String tipo, int capacidad, Set<String> equipamiento) {
}