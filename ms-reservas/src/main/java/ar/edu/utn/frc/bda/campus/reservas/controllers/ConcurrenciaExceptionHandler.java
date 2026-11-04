package ar.edu.utn.frc.bda.campus.reservas.controllers;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ar.edu.utn.frc.bda.campus.commons.ApiResponse;
import lombok.extern.slf4j.Slf4j;

/** Solo en ms-reservas: traduce los choques de concurrencia a 409 (RN15). */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConcurrenciaExceptionHandler {

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<Void>> choque(RuntimeException ex) {
        log.info("409 choque de concurrencia: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.create(false, null,
                "Otro usuario modificó ese espacio o esa reserva al mismo tiempo. Intentá de nuevo."));
    }
}