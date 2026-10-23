package ar.edu.utn.frc.bda.campus.commons;

import java.time.LocalDateTime;

/**
 * Formato único de respuesta de todas las APIs de CampusReservas
 * (el mismo del ejemplo de la cátedra).
 *
 * @param success       true si la operación salió bien
 * @param responseValue el dato que se devuelve (null en los errores)
 * @param message       texto que explica el resultado
 * @param executedOn    cuándo se generó la respuesta
 * @param <T>           el tipo del dato que se devuelve
 */
public record ApiResponse<T>(boolean success, T responseValue, String message, LocalDateTime executedOn) {

    /** Crea la respuesta completando la fecha y hora actual. */
    public static <T> ApiResponse<T> create(boolean success, T data, String message) {
        return new ApiResponse<>(success, data, message, LocalDateTime.now());
    }
}