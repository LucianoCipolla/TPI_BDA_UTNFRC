package ar.edu.utn.frc.bda.campus.commons;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import org.springframework.web.bind.MissingRequestHeaderException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduce las excepciones de negocio a respuestas HTTP con ApiResponse.
 * Se aplica a todos los controllers del microservicio que use este JAR.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiResponse<Void>> noEncontrado(RuntimeException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), req);                  // 404
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ApiResponse<Void>> denegado(RuntimeException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), req);                  // 403
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiResponse<Void>> conflicto(RuntimeException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), req);                   // 409
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiResponse<Void>> regla(RuntimeException ex, HttpServletRequest req) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), req);      // 422
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<ApiResponse<Void>> caido(RuntimeException ex, HttpServletRequest req) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req);        // 503
    }

    /** Falla de @Valid en un DTO: el pedido está mal formado → 400 con el detalle de cada campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalido(MethodArgumentNotValidException ex, HttpServletRequest req) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, detalle, req);
    }

    /** Falta un @RequestParam obligatorio → 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> faltaParametro(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Falta el parámetro obligatorio '" + ex.getParameterName() + "'", req);
    }

    /** Un parámetro no se puede convertir a su tipo (actividad=NADAR, id=abc, una fecha mal escrita) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Valor inválido para '" + ex.getName() + "': " + ex.getValue(), req);
    }

    /** El cuerpo no es un JSON válido, o trae un valor que no corresponde (un enum inexistente) → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST,
                "El cuerpo del pedido no es un JSON válido o tiene un valor que no corresponde", req);
    }

    /** Falta un @RequestHeader obligatorio (por ejemplo X-Usuario) → 400. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> faltaHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Falta el header obligatorio '" + ex.getHeaderName() + "'", req);
    }

    /**
     * Cualquier otra excepción. Las de Spring (ruta inexistente → 404, método no permitido → 405...)
     * conservan su código; el resto es un error nuestro → 500, con el stack trace en el log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> inesperado(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse er) {
            String detalle = er.getBody().getDetail() != null ? er.getBody().getDetail() : ex.getMessage();
            HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
            return error(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, detalle, req);
        }
        log.error("ERROR 500 en {}: excepción no controlada (ver stack trace)", pedido(req), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.create(false, null, "Error interno del servidor"));   // 500
    }

    /**
     * Arma la respuesta y deja una línea en el log que se entiende sola, por ejemplo:
     * "Rechazado 422 Unprocessable Content | POST /api/reservas (usuario: alumno1) | La reserva ya empezó".
     * Los 5xx van con WARN (algo externo falló); los 4xx con INFO (el pedido no cumplió una regla).
     */
    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String mensaje, HttpServletRequest req) {
        if (status.is5xxServerError()) {
            log.warn("Falla {} {} | {} | {}", status.value(), status.getReasonPhrase(), pedido(req), mensaje);
        } else {
            log.info("Rechazado {} {} | {} | {}", status.value(), status.getReasonPhrase(), pedido(req), mensaje);
        }
        return ResponseEntity.status(status).body(ApiResponse.create(false, null, mensaje));
    }

    /** "POST /api/reservas/5/cancelar (usuario: alumno1)": qué se pidió y quién lo pidió. */
    private static String pedido(HttpServletRequest req) {
        String usuario = req.getHeader("X-Usuario");
        return req.getMethod() + " " + req.getRequestURI()
                + (usuario != null ? " (usuario: " + usuario + ")" : "");
    }
}
