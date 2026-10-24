package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ar.edu.utn.frc.bda.campus.commons.ReglaNegocioException;

/** Eslabón 4: RN5, la capacidad que trajo ms-espacios. */
@Component
@Order(4)
public class ValidadorCapacidad extends ValidadorReserva {

    @Override
    protected void verificar(ContextoReserva ctx) {
        int capacidad = ctx.getEspacio().capacidad();
        if (ctx.getPedido().asistentes() > capacidad) {
            throw new ReglaNegocioException("El espacio admite hasta " + capacidad + " personas");
        }
    }
}