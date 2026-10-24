package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

import ar.edu.utn.frc.bda.campus.reservas.clients.ValidacionEspacio;
import ar.edu.utn.frc.bda.campus.reservas.entities.models.CrearReservaRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/** Lo que viaja por la cadena: el pedido y lo que los eslabones van averiguando. */
@Getter
@RequiredArgsConstructor
public class ContextoReserva {

    private final CrearReservaRequest pedido;
    private final String usuario;

    @Setter
    private ValidacionEspacio espacio;      // lo completa el eslabón que consulta a ms-espacios
}