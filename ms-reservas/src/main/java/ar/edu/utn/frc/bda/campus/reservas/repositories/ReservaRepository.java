package ar.edu.utn.frc.bda.campus.reservas.repositories;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.frc.bda.campus.reservas.entities.EstadoReserva;
import ar.edu.utn.frc.bda.campus.reservas.entities.Reserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    /** RN6: ¿el espacio ya tiene una reserva activa que se superpone con la franja? */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reserva r
            WHERE r.espacioId = :espacioId AND r.estado IN :estados
              AND r.inicio < :fin AND r.fin > :inicio
            """)
    boolean existeSuperposicion(Long espacioId, Collection<EstadoReserva> estados,
                                LocalDateTime inicio, LocalDateTime fin);

    /** Búsqueda de disponibles: los espacios con una reserva activa que se superpone con la franja. */
    @Query("""
            SELECT DISTINCT r.espacioId FROM Reserva r
            WHERE r.estado IN :estados AND r.inicio < :fin AND r.fin > :inicio
            """)
    Set<Long> espaciosOcupados(Collection<EstadoReserva> estados, LocalDateTime inicio, LocalDateTime fin);

    /** GET /api/reservas/mias */
    List<Reserva> findByUsuarioOrderByInicioAsc(String usuario);

    /** GET /api/reservas?estado=PENDIENTE (ADMIN) */
    List<Reserva> findByEstadoOrderByInicioAsc(EstadoReserva estado);

    /** GET /api/reservas (ADMIN) */
    List<Reserva> findAllByOrderByInicioAsc();
}