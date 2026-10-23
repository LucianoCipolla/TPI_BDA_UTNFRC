package ar.edu.utn.frc.bda.campus.commons;

/** El pedido está bien formado, pero viola una regla de negocio. Se responde 422. */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}