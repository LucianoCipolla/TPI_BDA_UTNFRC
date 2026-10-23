package ar.edu.utn.frc.bda.campus.commons;

/** Un servicio del que dependemos (ms-espacios u Open-Meteo) no respondió. Se responde 503. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}