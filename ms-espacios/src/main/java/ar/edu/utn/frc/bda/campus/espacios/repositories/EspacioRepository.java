package ar.edu.utn.frc.bda.campus.espacios.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.utn.frc.bda.campus.espacios.entities.Espacio;

/** Acceso a la tabla espacios. Spring Data genera la implementación. */
public interface EspacioRepository extends JpaRepository<Espacio, Long> {

    /** Los espacios con capacidad suficiente (filtro de la búsqueda de compatibles). */
    List<Espacio> findByCapacidadGreaterThanEqual(int capacidad);
}