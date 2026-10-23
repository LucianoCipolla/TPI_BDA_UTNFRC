package ar.edu.utn.frc.bda.campus.commons;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
/**
 * Traduce las excepciones de negocio a respuestas HTTP con ApiResponse.
 * Se aplica a todos los controllers del microservicio que use este JAR.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, RuntimeException ex) {
        return ResponseEntity.status(status).body(ApiResponse.create(false, null, ex.getMessage()));
    }
}