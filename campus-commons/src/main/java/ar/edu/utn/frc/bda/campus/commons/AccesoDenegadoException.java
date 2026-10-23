package ar.edu.utn.frc.bda.campus.commons;

/** El recurso existe, pero el usuario no puede operar sobre él (RN7). Se responde 403. */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
