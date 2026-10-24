package ar.edu.utn.frc.bda.campus.reservas.services.validadores;

/** Chain of Responsibility: cada eslabón valida una cosa y pasa el pedido al siguiente. */
public abstract class ValidadorReserva {

    private ValidadorReserva siguiente;

    void enlazar(ValidadorReserva siguiente) {
        this.siguiente = siguiente;
    }

    public final void validar(ContextoReserva ctx) {
        verificar(ctx);                      // si falla, lanza una excepción y la cadena se corta
        if (siguiente != null) {
            siguiente.validar(ctx);
        }
    }

    /** El control propio de este eslabón. */
    protected abstract void verificar(ContextoReserva ctx);
}