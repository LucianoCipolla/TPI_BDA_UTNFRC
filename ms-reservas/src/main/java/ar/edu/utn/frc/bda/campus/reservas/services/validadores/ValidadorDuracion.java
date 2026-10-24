package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Eslabón 2: RN2. */
@Component
@Order(2)
public class ValidadorDuracion extends ValidadorReserva {

    @Override
    protected void verificar(ContextoReserva ctx) {
        ReglasFranja.validarDuracion(ctx.getPedido().inicio(), ctx.getPedido().fin());
    }
}