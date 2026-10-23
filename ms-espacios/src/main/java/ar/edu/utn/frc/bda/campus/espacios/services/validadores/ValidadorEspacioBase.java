package ar.edu.utn.frc.bda.campus.espacios.services.validadores;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import ar.edu.utn.frc.bda.campus.espacios.entities.Equipamiento;
import ar.edu.utn.frc.bda.campus.espacios.entities.Espacio;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoActividad;
import ar.edu.utn.frc.bda.campus.espacios.entities.TipoEspacio;

/**
 * Strategy: el tipo común de todas las estrategias de validación (una subclase por tipo de espacio).
 * Template Method: primero las reglas comunes a todos los espacios, después las propias de cada tipo.
 */
public abstract class ValidadorEspacioBase {

    /** El tipo de espacio que atiende esta estrategia. Cada subclase está obligada a decirlo. */
    public abstract TipoEspacio tipo();

    /** El motivo por el que el espacio NO sirve para la franja, o null si sirve. */
    public final String motivoRechazo(Espacio e, LocalDateTime inicio, LocalDateTime fin, TipoActividad actividad, Set<Equipamiento> pedido) {
        // RN3: dentro del horario de apertura
        if (inicio.toLocalTime().isBefore(e.getHoraApertura()) || fin.toLocalTime().isAfter(e.getHoraCierre())) {
            return "Fuera del horario de apertura (" + e.getHoraApertura() + " a " + e.getHoraCierre() + ")";
        }
        // RN9: admite el tipo de actividad
        if (!e.getActividadesPermitidas().contains(actividad)) {
            return "El espacio no admite actividades de tipo " + actividad;
        }
        // RN8: tiene todo el equipamiento pedido
        Set<Equipamiento> faltante = new HashSet<>(pedido);
        faltante.removeAll(e.getEquipamiento());
        if (!faltante.isEmpty()) {
            return "Al espacio le falta: " + faltante;
        }
        // Lo que cambia según el tipo
        return reglasPropias(e, inicio, fin);
    }

    /** Las reglas propias del tipo. Por defecto, ninguna. */
    protected String reglasPropias(Espacio e, LocalDateTime inicio, LocalDateTime fin) {
        return null;
    }
}