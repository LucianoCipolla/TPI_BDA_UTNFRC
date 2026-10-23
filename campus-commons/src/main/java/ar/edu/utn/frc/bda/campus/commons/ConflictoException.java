package ar.edu.utn.frc.bda.campus.commons;

/** El pedido choca con el estado actual del recurso (RN6, RN14). Se responde 409. */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}