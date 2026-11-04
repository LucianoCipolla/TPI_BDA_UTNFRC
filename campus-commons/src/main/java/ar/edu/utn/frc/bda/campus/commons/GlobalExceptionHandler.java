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
/**
 * Traduce las excepciones de negocio a respuestas HTTP con ApiResponse.
 * Se aplica a todos los controllers del microservicio que use este JAR.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiResponse<Void>> noEncontrado(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex);                  // 404
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ApiResponse<Void>> denegado(RuntimeException ex) {
        return error(HttpStatus.FORBIDDEN, ex);                  // 403
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ApiResponse<Void>> conflicto(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex);                   // 409
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiResponse<Void>> regla(RuntimeException ex) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, ex);      // 422
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<ApiResponse<Void>> caido(RuntimeException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex);        // 503
    }

    /** Falla de @Valid en un DTO: el pedido está mal formado → 400 con el detalle de cada campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalido(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.create(false, null, detalle));
    }

        /** Falta un @RequestParam obligatorio → 400. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> faltaParametro(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.create(false, null,
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'"));
    }

    /** Un parámetro no se puede convertir a su tipo (actividad=NADAR, id=abc, una fecha mal escrita) → 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.create(false, null,
                "Valor inválido para '" + ex.getName() + "': " + ex.getValue()));
    }

    /** El cuerpo no es un JSON válido, o trae un valor que no corresponde (un enum inexistente) → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> jsonInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.create(false, null,
                "El cuerpo del pedido no es un JSON válido o tiene un valor que no corresponde"));
    }

        /** Falta un @RequestHeader obligatorio (por ejemplo X-Usuario) → 400. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> faltaHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.create(false, null,
                "Falta el header obligatorio '" + ex.getHeaderName() + "'"));
    }

    /**
     * Cualquier otra excepción. Las de Spring (ruta inexistente → 404, método no permitido → 405...)
     * conservan su código; el resto es un error nuestro → 500, con el stack trace en el log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> inesperado(Exception ex) {
        if (ex instanceof ErrorResponse er) {
            String detalle = er.getBody().getDetail() != null ? er.getBody().getDetail() : ex.getMessage();
            log.info("{} {}", er.getStatusCode().value(), detalle);
            return ResponseEntity.status(er.getStatusCode()).body(ApiResponse.create(false, null, detalle));
        }
        log.error("Error inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.create(false, null, "Error interno del servidor"));   // 500
    }

    /** Los 5xx van con WARN (algo externo falló); los 4xx con INFO (el pedido no cumplió una regla). */
    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, RuntimeException ex) {
        if (status.is5xxServerError()) {
            log.warn("{} {}", status.value(), ex.getMessage());
        } else {
            log.info("{} {}", status.value(), ex.getMessage());
        }
        return ResponseEntity.status(status).body(ApiResponse.create(false, null, ex.getMessage()));
    }
}