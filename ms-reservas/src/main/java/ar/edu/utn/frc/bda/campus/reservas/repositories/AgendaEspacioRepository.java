package ar.edu.utn.frc.bda.campus.reservas.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import ar.edu.utn.frc.bda.campus.reservas.entities.AgendaEspacio;
import jakarta.persistence.LockModeType;

public interface AgendaEspacioRepository extends JpaRepository<AgendaEspacio, Long> {

    /** Lee la fila y obliga a JPA a incrementar su versión al hacer commit, aunque no cambie nada más (RN15). */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    Optional<AgendaEspacio> findByEspacioId(Long espacioId);
}