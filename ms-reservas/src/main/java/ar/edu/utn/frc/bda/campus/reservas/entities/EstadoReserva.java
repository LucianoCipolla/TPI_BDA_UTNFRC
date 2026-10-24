package ar.edu.utn.frc.bda.campus.reservas.entities;

import java.util.EnumSet;
import java.util.Set;

/** Los estados de una reserva y los cambios permitidos entre ellos (RN13 y RN14). */
public enum EstadoReserva {
    PENDIENTE, CONFIRMADA, RECHAZADA, CANCELADA;

    /** Los estados que ocupan el espacio (RN6 y búsqueda de disponibles). */
    public static final Set<EstadoReserva> ACTIVOS = EnumSet.of(PENDIENTE, CONFIRMADA);

    /** RN14: los cambios permitidos, iguales al diagrama de estados. */
    public boolean puedePasarA(EstadoReserva destino) {
        return switch (this) {
            case PENDIENTE -> destino == CONFIRMADA || destino == RECHAZADA || destino == CANCELADA;
            case CONFIRMADA -> destino == CANCELADA;
            case RECHAZADA, CANCELADA -> false;
        };
    }
}