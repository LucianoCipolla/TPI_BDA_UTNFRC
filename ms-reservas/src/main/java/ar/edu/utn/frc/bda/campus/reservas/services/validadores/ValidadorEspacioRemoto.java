package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ar.edu.utn.frc.bda.campus.commons.ReglaNegocioException;
import ar.edu.utn.frc.bda.campus.reservas.clients.EspaciosClient;
import ar.edu.utn.frc.bda.campus.reservas.clients.ValidacionEspacio;
import ar.edu.utn.frc.bda.campus.reservas.entities.models.CrearReservaRequest;
import lombok.RequiredArgsConstructor;

/** Eslabón 3: le pregunta a ms-espacios si el espacio sirve (RN3, RN4, RN8 a RN12). */
@Component
@Order(3)
@RequiredArgsConstructor
public class ValidadorEspacioRemoto extends ValidadorReserva {

    private final EspaciosClient espaciosClient;

    @Override
    protected void verificar(ContextoReserva ctx) {
        CrearReservaRequest p = ctx.getPedido();
        ValidacionEspacio v = espaciosClient.validar(p.espacioId(), p.inicio(), p.fin(), p.actividad(), p.equipamiento());
        if (!v.disponible()) {
            throw new ReglaNegocioException(v.motivo());
        }
        ctx.setEspacio(v);
    }
}