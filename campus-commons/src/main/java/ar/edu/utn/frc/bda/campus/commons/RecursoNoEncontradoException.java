package ar.edu.utn.frc.bda.campus.commons;

/** Lo pedido no existe (por ejemplo, un espacio o una reserva). Se responde 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}