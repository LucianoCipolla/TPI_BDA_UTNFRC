package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

import java.util.List;

import org.springframework.stereotype.Component;

/** Arma la cadena una sola vez, en el orden de @Order, y guarda el primer eslabón. */
@Component
public class CadenaValidacionReserva {

    private final ValidadorReserva primero;

    public CadenaValidacionReserva(List<ValidadorReserva> eslabones) {   // Spring los entrega ordenados por @Order
        for (int i = 0; i < eslabones.size() - 1; i++) {
            eslabones.get(i).enlazar(eslabones.get(i + 1));
        }
        this.primero = eslabones.get(0);
    }

    public void validar(ContextoReserva ctx) {
        primero.validar(ctx);
    }
}