package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Eslabón 1: RN1. */
@Component
@Order(1)
public class ValidadorAnticipacion extends ValidadorReserva {

    @Override
    protected void verificar(ContextoReserva ctx) {
        ReglasFranja.validarAnticipacion(ctx.getPedido().inicio());
    }
}