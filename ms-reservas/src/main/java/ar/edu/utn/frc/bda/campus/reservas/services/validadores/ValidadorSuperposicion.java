package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ar.edu.utn.frc.bda.campus.commons.ConflictoException;
import ar.edu.utn.frc.bda.campus.reservas.entities.EstadoReserva;
import ar.edu.utn.frc.bda.campus.reservas.entities.models.CrearReservaRequest;
import ar.edu.utn.frc.bda.campus.reservas.repositories.ReservaRepository;
import lombok.RequiredArgsConstructor;

/** Eslabón 5: RN6, que no haya otra reserva activa que se superponga. */
@Component
@Order(5)
@RequiredArgsConstructor
public class ValidadorSuperposicion extends ValidadorReserva {

    private final ReservaRepository repository;

    @Override
    protected void verificar(ContextoReserva ctx) {
        CrearReservaRequest p = ctx.getPedido();
        if (repository.existeSuperposicion(p.espacioId(), EstadoReserva.ACTIVOS, p.inicio(), p.fin())) {
            throw new ConflictoException("El espacio ya está reservado en esa franja");
        }
    }
}